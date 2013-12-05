package routing;
import core.Connection;
import core.DTNHost;
import core.Message;
import core.Settings;
import core.SimClock;
import core.Tuple;

import java.util.*;
/**
 * 利用社团指导路由转发
 * 对Panhui的分布式社团发现算法进行改进，引入aging机制，滑动窗口
 * @author Jesse
 *2013.3.31
 */
public class CommunityRouter extends ActiveRouter{

	/** Community router's setting namespace ({@value})*/
	public static final String COMMUNITY_NS = "CommunityRouter";
	
	/** 设置k-clique 算法的k值*/
	public static final String CLIQUE_VALUE = "clique";
	
	/** 设置节点进入社团的阈值 为相遇次数*/
	public static final String ENTER_COMMUNITY_THRE = "entercommunity";

	/** 设置滑动窗口的阈值 默认为6小时*/
	public static final String WINDOW_SIZE = "window";

	private int cliqueValue;   // the k value of the k-clique	
	private int communityThre;   // 进入社团的阈值，为相遇次数
	private int windowSize;   // 滑动窗口的大小
	/**记录和其它节点在滑动窗口内的相遇次数*/
	private Map<DTNHost, Double> recordNodesTimes;
	/** 记录相遇时刻*/
	private Map<DTNHost, LinkedList<Double>> recordEncounterTimes;
	/**记录和社团内部节点的距离 更新过程随着社团的更新而更新 <nodeNum,dis>*/
	private Set<DTNHost> communitySet;   // 记录社团
	private Set<DTNHost> familaritySet;   // 记录熟悉集
	private HashMap<DTNHost, Set<DTNHost>> fsolc;   // FSoLCo
	
	private Set<DTNHost> mostFrequent;   // when not too enough nodes in the community,
										//regard this as the community
	private final int TIME_GAP = 60*10;   // every 10 minutes to update mostFrequent 
	private double lastUpdateTime = 0;   // record the last update time
	private void initDistance(){
		recordNodesTimes = new HashMap<DTNHost, Double>();
		recordEncounterTimes = new HashMap<DTNHost,LinkedList<Double>>();
		//distance = new HashMap<DTNHost, Integer>();
		communitySet = new HashSet<DTNHost>();
		familaritySet = new HashSet<DTNHost>();
		fsolc = new HashMap<DTNHost,Set<DTNHost>>();	
		mostFrequent = new HashSet<DTNHost>();
	}
	
	/**
	 * Constructor. Creates a new message router based on the settings in
	 * the given Settings object.
	 * @param s The settings object
	 */
	public CommunityRouter(Settings s){
		// 从配置文件中读取一些配置的阈值
		super(s);
		Settings communitySettings = new Settings(COMMUNITY_NS);
		if(communitySettings.contains(CLIQUE_VALUE))
			cliqueValue = communitySettings.getInt(CLIQUE_VALUE);
		else
			System.out.println("no read the cliqueValue form the settings file");
			//cliqueValue = 0;
		
		if(communitySettings.contains(ENTER_COMMUNITY_THRE))
			communityThre = communitySettings.getInt(ENTER_COMMUNITY_THRE);
		else
			System.out.println("no read the communityThre form the settings file");
			//communityThre = 0;
		
		if(communitySettings.contains(WINDOW_SIZE))
			windowSize = communitySettings.getInt(WINDOW_SIZE);
		else
			System.out.println("no read the windowSize form the settings file");
			//windowSize = 0;
		
		initDistance();
	}
	
	/**
	 * Copy constructor.
	 * @param r The router prototype where setting values are copied from
	 */
	protected CommunityRouter(CommunityRouter r){
		super(r);
		// TODO Auto-generated constructor stub
		// 可以访问私有的成员变量？
		this.cliqueValue = r.cliqueValue;
		this.communityThre = r.communityThre;
		this.windowSize = r.windowSize;
		initDistance();
	}

	@Override
	public void changedConnection(Connection con){
		if(con.isUp()){
			DTNHost otherHost = con.getOtherNode(getHost());
			updateMeetTimes(otherHost);
			exchangeCommunity(otherHost);
		}
	}
	
	/**
	 * 相当于DisComDet中Node类中的updateTimes方法
	 * 当遇到一个节点的时候，该函数将另一个节点加入到该节点相遇列表中
	 * 并更新和所有其他节点在滑动窗口内的相遇次数 
	 * 要不要对社团内的节点进行更新？
	 * @param host
	 */
	public void updateMeetTimes(DTNHost host){
		double currentTime = SimClock.getTime();   // current time
		if(recordEncounterTimes.get(host) == null){
			recordEncounterTimes.put(host,new LinkedList<Double>());
		}
		recordEncounterTimes.get(host).addLast(currentTime);
		// 注意第一次连接的情况  beginTime为节点对间最早建立连接的时刻 应该对连接的每一个节点进行检查
		for(Map.Entry<DTNHost, LinkedList<Double>> entry :recordEncounterTimes.entrySet()){
			DTNHost keyValue = entry.getKey();
			if(recordEncounterTimes.get(keyValue).isEmpty()) // 清除节点 直至为空
				continue;
			double beginTime = recordEncounterTimes.get(keyValue).getFirst();
			int deleteNum = 0;
			while(currentTime - beginTime > windowSize){
				recordEncounterTimes.get(keyValue).removeFirst();
				deleteNum++;
				if(recordEncounterTimes.get(keyValue).isEmpty()) // 清除节点 直至为空
					break;
				beginTime = recordEncounterTimes.get(keyValue).getFirst();
			}
			
			recordNodesTimes.put(keyValue, recordNodesTimes.get(keyValue) == null
					?0:recordNodesTimes.get(keyValue)-deleteNum);
		}	
		// 节点对间上次累积相遇次数
		double counts = recordNodesTimes.get(host) == null?0:recordNodesTimes.get(host);
		recordNodesTimes.put(host, counts+1);
	}
	/**
	 * 对应于DisComDet中的exchangeMessage
	 * @param host 相遇的另一个节点
	 */
	public void exchangeCommunity(DTNHost host){
		// 仅使用累积时间作为阈值的标准，还应该考虑相遇间隔时长 考虑将节点长时间不相遇，从集合中移除？
		double time = recordNodesTimes.get(host) == null?0:recordNodesTimes.get(host);
		if(time > communityThre){   //分布式社团发现步骤1
			familaritySet.add(host);
			communitySet.add(host);
			fsolc.put(host, ((CommunityRouter)host.getRouter()).getFamilaritySet());
			mergeTwoCommunity(host,cliqueValue);   // 步骤3  当节点在上两个步骤中加入社团时，合并两个社团,
		}else{   //步骤2
			// 相遇节点的familarity set 与 本节点的community的交集   若两个节点的familarity set的交集？
			int intersectionSize = calcTwoSetIntersection(communitySet, ((CommunityRouter)
					host.getRouter()).getFamilaritySet());
			if(intersectionSize > cliqueValue-2){ //k-clique
				communitySet.add(host);
				fsolc.put(host, ((CommunityRouter)host.getRouter()).getFamilaritySet());   // another.getFamilaritySet()
				// 步骤3  当节点在上两个步骤中加入社团时，合并两个社团
				mergeTwoCommunity(host,cliqueValue);
			}
		}
		// 从集合中删除滑动窗口时间内连接次数小于阈值的节点
		deleteNodeInFamilarityAndCommuSet(SimClock.getTime());
	}
	
	/**
	 * 对familaritySet中的每一个节点，查找其在recordEncounterTimes中相遇的时刻，如果
	 * 该时刻超出滑动窗口的范围，则从familaritySet中删除该节点 从社团中删除
	 * 应该从加入社团相反的角度考虑删除社团内的节点
	 * @param currentTime
	 */
	public void deleteNodeInFamilarityAndCommuSet(double currentTime){
		// 遍历集合中的节点
		for(Iterator<DTNHost> itr = familaritySet.iterator(); itr.hasNext();){
			DTNHost host = itr.next();
			if(recordEncounterTimes.get(host).isEmpty())   // 不在相遇列表中 删除
				itr.remove();
			else if(this.recordNodesTimes.get(host) < this.communityThre){
				itr.remove();   //小于进入熟悉集的阈值，删除.  2013.4.15
			}
		}
		// 删除社团中的节点
		for(Iterator<DTNHost> itr = communitySet.iterator(); itr.hasNext();){
			DTNHost host = itr.next();
			if(recordNodesTimes.get(host) == null){
				itr.remove();
			}
			else if(recordNodesTimes.get(host) > this.communityThre){   // 直接加入社团
				continue;
			}
			else if(calcTwoSetIntersection(communitySet, ((CommunityRouter)
					host.getRouter()).getFamilaritySet()) > cliqueValue-2){   // merge into
				continue;
			}
			else
				itr.remove();
		}
	}
	
	public Set<DTNHost> getFamilaritySet(){
		return familaritySet;
	}
	
	// 根据节点号得到fsolc中的一个^Fi
	public Set<DTNHost> getFsolc(DTNHost host){
		if(fsolc.containsKey(host))
			return fsolc.get(host);
		else{
			// 如何进行错误处理？
			return null;
		}	
	}
	
	public Set<DTNHost> getCommunitySet(){
		return communitySet;
	}
	
	/**
	 * 合并两个社团，用于分布式社团发现算法的步骤3
	 * 对于另一个节点中社团的每一个点，对每一个点维护的每一个熟悉集，
	 * 计算该集合和本节点社团的交集，若大于k-1，加入当前节点的社团
	 * @param anotherNode
	 * @param threshold the k value
	 */
	public void mergeTwoCommunity(DTNHost anotherHost, int threshold){
		int intersectionSize = 0;
		for(DTNHost i:((CommunityRouter)anotherHost.getRouter()).getCommunitySet()){
			Set<DTNHost> familaritySet = ((CommunityRouter)anotherHost.getRouter()).getFsolc(i);   //anotherHost.getFsolc(i)
			intersectionSize = calcTwoSetIntersection(communitySet,familaritySet);
			if(intersectionSize > threshold-2){
				communitySet.add(i);
				fsolc.put(i, ((CommunityRouter)anotherHost.getRouter()).getFsolc(i));// add Fi to FSoLCo
			}
		}
	}	
	
	/**
	 * 计算两个集合的交集
	 * @param first
	 * @param second
	 * @return 交集的大小
	 */
	public int calcTwoSetIntersection(Set<DTNHost> first, Set<DTNHost> second){
		// empty set
		if(first == null || second == null)
			return 0;
		if(first.size() == 0 || second.size() == 0)
			return 0;
		Set<DTNHost> resultSet = new HashSet<DTNHost>();
		resultSet.addAll(first);
		
		resultSet.retainAll(second);
		return resultSet.size();
	}	
	
	@Override
	public void update(){
		super.update();
		
		if (isTransferring() || !canStartTransfer()) {
			return; // transferring, don't try other connections yet
		}
		
		// Try first the messages that can be delivered to final recipient
		if (exchangeDeliverableMessages() != null) {
			return; // started a transfer, don't try others (yet)
		}
		
		tryOtherMessages();
	}
	
	/**
	 * Tries to send all other messages to all connected hosts ordered by
	 * their delivery probability
	 * @return The return value of {@link #tryMessagesForConnected(List)}
	 */
	private Tuple<Message, Connection> tryOtherMessages() {
		List<Tuple<Message, Connection>> messages = 
			new ArrayList<Tuple<Message, Connection>>(); 
	
		Collection<Message> msgCollection = getMessageCollection();
		
		/* for all connected hosts collect all messages that have a higher
		   probability of delivery by the other host */
		for (Connection con : getConnections()) {
			DTNHost other = con.getOtherNode(getHost());
			CommunityRouter othRouter = (CommunityRouter)other.getRouter();
			
			if (othRouter.isTransferring()) {
				continue; // skip hosts that are transferring
			}
			
			for (Message m : msgCollection) {
				if (othRouter.hasMessage(m.getId())) {
					continue; // skip messages that the other one has
				}   // otrRouter.distance() > distance() 使用距离替代概率
				if (isForward2(othRouter, m.getTo())) { // othRouter.getDistance(m.getTo()) > getDistance(m.getTo())
					// the other node has higher probability of delivery
					messages.add(new Tuple<Message, Connection>(m,con));
				}
			}			
		}
		
		if (messages.size() == 0) {
			return null;
		}	
		// sort the message-connection tuples
		// Collections.sort(messages, new TupleComparator());
		return tryMessagesForConnected(messages);	// try to send messages
	}
	
	/**
	 * 判断本节点，中间节点和目的节点之间的距离 根据距离判断是否转发给中间节点
	 * 由于ret返回值大多数为false，造成仿真实验结果的跳数大多数为一跳。
	 * @param byRouter
	 * @param destination
	 * @return
	 */
	private boolean isForward(CommunityRouter byRouter, DTNHost destination){
		boolean ret = false;
		
		if(isInSameComm(byRouter, destination)){
			// 中间节点离目的节点更近 getDistance返回跳数
			if(this.getDistance(destination) > byRouter.getDistance(destination))
				ret = true;
		}   // 三个节点在同一个社团
		else if(this.isInSameComm(byRouter.getHost()) && byRouter.isInSameComm(destination)){
			ret = true;
		}   // 三个节点在两个社团 中间节点是桥节点
		else{   // 目的节点社图为空
			if(this.comDis(destination) < byRouter.comDis(destination))   // 值更大 说明共同节点更多
				ret = true;
		}// 三个节点在三个社团
		ret = false;   //
		return ret;
	}
	
	/**
	 * 判断是否进行转发策略二，判断目的节点是否在当前节点的社团内，以及社团内部节点的社团内
	 * 若在，不进行转发，
	 * 否则，判断中间节点是否在当前节点的社团内，若不再，转发到中间节点，让报文有机会转发出社团。
	 * 
	 * 实验结果说明，跳数太多，应该采用第一种转发算法更合理
	 * 实验方案，阈值设置的小一点，让更多的节点在社团内 
	 * 或者不用自己修改后的社团发现算法，使用原始PanHui社团发现算法
	 * @param byRouter
	 * @param destination
	 * @return
	 */
	private boolean isForward2(CommunityRouter byRouter, DTNHost destination){
		// boolean ret = false;
		if(isInSameComm(destination)){   // 目的节点和当前节点在同一个社团，不进行转发
			return false;
		}
		if(byRouter.isInSameComm(destination)){   // 判断目的节点是否在中间节点的社团内 若在，转发给中间节点
			return true; 	
		}
		if(isInFamiloSet(destination)){   // 判断是否在当前节点所在的社团内的节点的社团内，若在，不转发到中间节点
			return false;
		}
		if(isInSameComm(byRouter.getHost()))   // 判断中间节点是否在当前节点的社团内，若在，不要转发
			return false;
		if(byRouter.isInFamiloSet(destination)){   // 判断是否在中间节点社团内节点的社团内，若在进行转发
			return true;
		}
			// return true;   // 将不再当前节点社团内的报文转发出去，有机会遇到目的节点 问题是跳数
		// 太多，认为不合理
		return false;   // 平均跳数为一
	} 

	
	/**
	 * 当两个节点不在一个社团，计算该节点所在社团和另一个节点所在社团的距离
	 * 该距离使用两个社团间相同节点的数量 该值越大，说明距离越近
	 * @param anotherNode
	 * @return 但两个社团有交集时，返回交集的大小，否则返回Integer.MAX_VALUE
	 */
	public int comDis(DTNHost anotherNode){
		int dis = Integer.MAX_VALUE;
		if(anotherNode == null){   // exception
			System.out.println("empty node");
		}
		Set<DTNHost> anotherHostFamSet = ((CommunityRouter)anotherNode.getRouter()).getFamilaritySet();
		dis = calcTwoSetIntersection(communitySet,anotherHostFamSet);   // anotherNode.getCommunitySet()
		if(dis == 0)
			dis = Integer.MAX_VALUE;
		return dis;
	}
	
	/**
	 * 当两个节点位于同一个社团时，计算两个节点间的距离
	 * 该距离使用跳数，认为从熟悉集中得到的节点跳数为1
	 * 从其它节点处得到的节点跳数为2
	 * @param anothreNode
	 * @return 通常值为1或者2，当返回值为Integer.MAX_VALUE，说明不再同一个社团内。
	 */
	public int nodeDis(DTNHost anothreNode){
		int dis = Integer.MAX_VALUE;
//		if(distance.containsKey(anothreNode))
//			dis = distance.get(anothreNode);
		return dis;
	}
	
	/**
	 * 当遇到另一个节点时，计算其距离，合并节点间距离与社团间距离 是DisComDet中的dist函数
	 * 首先，若是同一个社团内的节点，返回跳数
	 * 之后，若是不同社团的节点，返回相同节点的数量
	 * @param anotherNode
	 * @return
	 */
	public int getDistance(DTNHost anotherNode){
		int dis = 0;
		int base = 3;
		if(isInSameComm(anotherNode)){   // in the same community
			return nodeDis(anotherNode);   // 1 or 2
		}
		else{
			dis = comDis(anotherNode);
			if (dis != Integer.MAX_VALUE)
				dis += base;
			return dis;   // 社团间的距离加上一个base和节点间的距离进行区分
		}
		//return dis;
	}
	
	/**
	 * 判断两个节点是否在同一个社团 使用一个节点是否在另一个节点的社团内
	 * 特殊情况 当一个社团内部的节点过少时，将熟悉集中的部分节点加入到社团中
	 * @param anotherNode
	 * @return 若在同一个社团 返回ture 否则false
	 */
	public boolean isInSameComm(DTNHost anotherNode){
		boolean ret = false;
		if(communitySet.size() < 3){   // 若社团中的节点过少 最常想遇的5个节点作为社团中的节点
			if(SimClock.getIntTime() - this.lastUpdateTime > this.TIME_GAP){
				lastUpdateTime = SimClock.getIntTime();
				// sort the map
				List<Map.Entry<DTNHost, Double>> list = 
					new ArrayList<Map.Entry<DTNHost, Double>>(recordNodesTimes.entrySet());
				Collections.sort(list, new Comparator<Map.Entry<DTNHost, Double>>(){
					public int compare(Map.Entry<DTNHost, Double> o1,Map.Entry<DTNHost, Double> o2){	
						return (o1.getValue().toString().compareTo(o2.getValue().toString()));
					}
				});
				// 遍历列表的前5个常相遇节点，作为其社团内的节点
				this.mostFrequent.clear();
				int len = 5;
				if(list.size() < 5)
					len = list.size();
				for(int i=0; i<len; i++){
					mostFrequent.add(list.get(i).getKey());
				}
			}
			if(mostFrequent.contains(anotherNode))
				ret = true;
		}
		else if(anotherNode != null){
			ret = communitySet.contains(anotherNode);
		}
		return ret;
	}
	
	/**
	 * 判断三个节点是否在同一个社团
	 * @param byRouter
	 * @param destination
	 * @return
	 */
	public boolean isInSameComm(CommunityRouter byRouter,DTNHost destination){
		boolean ret = false;
		if(communitySet.contains(byRouter.getHost()) || communitySet.contains(destination))
			ret = true;
		return ret;
	}
	
	/**
	 *  判断另一个节点（目的节点）是否在当前节点社团内节点的社团内
	 *  对社团中的每一个节点，遍历Familarity set of local community
	 * @param anotherNode
	 * @return
	 */
	public boolean isInFamiloSet(DTNHost anotherNode){
		for(DTNHost h: this.familaritySet){
			if(this.fsolc.get(h).contains(anotherNode))
				return true;
		}
		return false;
		
		// for each elements in fsolc
//		for(Map.Entry<DTNHost, Set<DTNHost>> e : fsolc.entrySet()){
//			
//		}
	}
	
	/**
	 * 实现单拷贝
	 */
	@Override
	protected void transferDone(Connection con) {
		/* don't leave a copy for the sender */
		this.deleteMessage(con.getMessage().getId(), false);
	}
	
	@Override
	public CommunityRouter replicate() {
		// TODO Auto-generated method stub
		return new CommunityRouter(this);
	}

}
