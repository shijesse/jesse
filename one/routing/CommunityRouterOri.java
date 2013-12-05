package routing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import core.Connection;
import core.DTNHost;
import core.Message;
import core.Settings;
import core.SimClock;
import core.Tuple;

/**
 * 使用原始的PanHui社团检测算法
 * @author Jesse
 * 2013.4.6
 */
public class CommunityRouterOri extends ActiveRouter{

	/** Community router's setting namespace ({@value})*/
	public static final String COMMUNITY_NS = "CommunityRouterOri";
	
	/** 设置k-clique 算法的k值*/
	public static final String CLIQUE_VALUE = "clique";
	
	/** 设置节点进入社团的阈值 为相遇次数*/
	public static final String ENTER_COMMUNITY_THRE = "entercommunity";

	private int cliqueValue;   // the k value of the k-clique	
	private int communityThre;   // 进入社团的阈值，为相遇次数
	
	private Map<DTNHost, Double> recordNodesTimes;   // 记录和其它节点在滑动窗口内的相遇次数
//	private Map<DTNHost, LinkedList<Double>> recordEncounterTimes;   // 记录相遇时刻
	private Map<DTNHost, Integer> distance;   // 记录和社团内部节点的距离 更新过程随着社团的更新而更新 <nodeNum,dis>
	private Set<DTNHost> communitySet;   // 记录社团
	private Set<DTNHost> familaritySet;   // 记录熟悉集
	private HashMap<DTNHost, Set<DTNHost>> fsolc;   // FSoLCo
	
	private void initDistance(){
		recordNodesTimes = new HashMap<DTNHost, Double>();
//		recordEncounterTimes = new HashMap<DTNHost,LinkedList<Double>>();
		distance = new HashMap<DTNHost, Integer>();
		communitySet = new HashSet<DTNHost>();
		familaritySet = new HashSet<DTNHost>();
		fsolc = new HashMap<DTNHost,Set<DTNHost>>();	
	}
	
	/**
	 * Constructor. Creates a new message router based on the settings in
	 * the given Settings object.
	 * @param s The settings object
	 */
	public CommunityRouterOri(Settings s){
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
		
		initDistance();
	}
	
	/**
	 * Copy constructor.
	 * @param r The router prototype where setting values are copied from
	 */
	protected CommunityRouterOri(CommunityRouterOri r){
		super(r);
		// TODO Auto-generated constructor stub
		// 可以访问私有的成员变量？
		this.cliqueValue = r.cliqueValue;
		this.communityThre = r.communityThre;
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
	 * @param host
	 */
	public void updateMeetTimes(DTNHost host){
//		double currentTime = SimClock.getTime();   // current time
//		if(recordEncounterTimes.get(host) == null){
//			recordEncounterTimes.put(host,new LinkedList<Double>());
//		}
//		recordEncounterTimes.get(host).addLast(currentTime);
		// 节点对间上次累积相遇次数
		double counts = recordNodesTimes.get(host) == null?0:recordNodesTimes.get(host);
		recordNodesTimes.put(host, counts+1);	
	}
	/**
	 * 对应于DisComDet中的exchangeMessage
	 * @param host 相遇的另一个节点
	 */
	public void exchangeCommunity(DTNHost host){
		// 仅使用累积时间作为阈值的标准，还应该考虑相遇间隔时长
		// 考虑将节点长时间不相遇，从集合中移除？
		double time = recordNodesTimes.get(host) == null?0:recordNodesTimes.get(host);
		if(time > communityThre){   //分布式社团发现步骤1
			//int anotherNodeNum = another.getNodeNumber();
			familaritySet.add(host);
			communitySet.add(host);
			fsolc.put(host, ((CommunityRouterOri)host.getRouter()).getFamilaritySet());   //another.getFamilaritySet()
			distance.put(host, 1);   // 经常相遇节点距离为1
			// 步骤3  当节点在上两个步骤中加入社团时，合并两个社团, threshold 的
			mergeTwoCommunity(host,cliqueValue);
			
		}else{   //步骤2
			// 相遇节点的familarity set 与 本节点的community的交集
			int intersectionSize = calcTwoSetIntersection(communitySet, ((CommunityRouterOri)host.getRouter()).getFamilaritySet());   // another.getFamilaritySet()
			if(intersectionSize >= cliqueValue-1){ //k-clique
				communitySet.add(host);
				if(!distance.containsKey(host))
					distance.put(host, 2); // 通过k-clique合并的 设置距离为2
				fsolc.put(host, ((CommunityRouterOri)host.getRouter()).getFamilaritySet());   // another.getFamilaritySet()
				// 步骤3  当节点在上两个步骤中加入社团时，合并两个社团, threshold 的
				mergeTwoCommunity(host,cliqueValue);
			}else{ // 从集合中删除滑动窗口时间内连接次数小于阈值的节点
				
			}
		}
		// 从集合中删除滑动窗口时间内连接次数小于阈值的节点
		// deleteNodeInFamilarityAndCommuSet(SimClock.getTime());
	}
	
	/**
	 * 对familaritySet中的每一个节点，查找其在recordEncounterTimes中相遇的时刻，如果
	 * 该时刻超出滑动窗口的范围，则从familaritySet中删除该节点 从社团中删除
	 * @param currentTime
	 */
	
//	public void deleteNodeInFamilarityAndCommuSet(double currentTime){
//		// 遍历集合中的节点
//		for(Iterator<DTNHost> itr = familaritySet.iterator(); itr.hasNext();){
//			DTNHost host = itr.next();
//			if(recordEncounterTimes.get(host).isEmpty())
//				itr.remove();
//			
//		}
//		// 删除社团中的节点
//		for(Iterator<DTNHost> itr = communitySet.iterator(); itr.hasNext();){
//			DTNHost host = itr.next();
//			if(recordEncounterTimes.get(host) == null)
//				continue;
//			if(recordEncounterTimes.get(host).isEmpty()){
//				itr.remove();
//				distance.remove(host);   // 同时删除节点间的距离
//			}
//		}
//	}
	
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
	 * @param anotherNode
	 * @param threshold the k value
	 */
	public void mergeTwoCommunity(DTNHost anotherHost, int threshold){
		int intersectionSize = 0;
		for(DTNHost i:((CommunityRouterOri)anotherHost.getRouter()).getCommunitySet()){
			Set<DTNHost> familaritySet = ((CommunityRouterOri)anotherHost.getRouter()).getFsolc(i);   //anotherHost.getFsolc(i)
			intersectionSize = calcTwoSetIntersection(communitySet,familaritySet);
			if(intersectionSize >= threshold-1){
				communitySet.add(i);
				if(!distance.containsKey(i))
					distance.put(i, 2);
				// add Fi to FSoLCo
				fsolc.put(i, ((CommunityRouterOri)anotherHost.getRouter()).getFsolc(i));
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
			CommunityRouterOri othRouter = (CommunityRouterOri)other.getRouter();
			
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
//	private boolean isForward(CommunityRouterOri byRouter, DTNHost destination){
//		boolean ret = false;
//		
//		if(isInSameComm(byRouter, destination)){
//			// 中间节点离目的节点更近 getDistance返回跳数
//			if(this.getDistance(destination) > byRouter.getDistance(destination))
//				ret = true;
//		}   // 三个节点在同一个社团
//		else if(this.isInSameComm(byRouter.getHost()) && byRouter.isInSameComm(destination)){
//			ret = true;
//		}   // 三个节点在两个社团 中间节点是桥节点
//		else{   // 目的节点社图为空
//			if(this.comDis(destination) < byRouter.comDis(destination))   // 值更大 说明共同节点更多
//				ret = true;
//		}// 三个节点在三个社团
//		// ret = true;   // 查看跳数的变化 应该不是1了 造成的问题是由于要转发大量的报文，仿真时间很慢
//		ret = false;
//		return ret;
//	}
	
	/**
	 * 判断是否进行转发策略二，判断目的节点是否在当前节点的社团内，以及社团内部节点的社团内
	 * 若在，不进行转发，否则，转发到中间节点，让报文有机会转发出社团。
	 * 实验结果说明，跳数太多，应该采用第一种转发算法更合理
	 * 实验方案，阈值设置的小一点，让更多的节点在社团内 
	 * 或者不用自己修改后的社团发现算法，使用原始PanHui社团发现算法
	 * @param byRouter
	 * @param destination
	 * @return 实验结果表明，该算法还是比较有效的
	 */
	private boolean isForward2(CommunityRouterOri byRouter, DTNHost destination){
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
		return false;   // 平均跳数为1 太小 也不合理 根据六度分割理论 理想值为6
	}
	/**
	 * isForward2仅仅考虑节点在不同集合中的位置
	 * 该算法考虑跳数
	 * @param byRouter
	 * @param destination
	 * @return
	 */
	private boolean isForward3(CommunityRouterOri byRouter, DTNHost destination){
		if(isInSameComm(destination)  ){   // 目的节点和当前节点在同一个社团，但中间节点更近，则转发
			return true;						// 用过去一段时间内相遇次数衡量
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
		Set<DTNHost> anotherHostFamSet = ((CommunityRouterOri)anotherNode.getRouter()).getFamilaritySet();
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
		if(distance.containsKey(anothreNode))
			dis = distance.get(anothreNode);
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
	 * 判断两个节点是否在同一个社团
	 * 使用一个节点是否在另一个节点的社团内
	 * @param anotherNode
	 * @return 若在同一个社团 返回ture 否则false
	 */
	public boolean isInSameComm(DTNHost anotherNode){
		boolean ret = false;
		if(anotherNode != null){
			ret = communitySet.contains(anotherNode);
		}
		else
			System.out.println("empty node in the function isInSameComm");
		return ret;
	}
	
	/**
	 * 判断三个节点是否在同一个社团
	 * @param byRouter
	 * @param destination
	 * @return
	 */
	public boolean isInSameComm(CommunityRouterOri byRouter,DTNHost destination){
		boolean ret = false;
		if(communitySet.contains(byRouter.getHost()) && communitySet.contains(destination))
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
	public CommunityRouterOri replicate() {
		// TODO Auto-generated method stub
		return new CommunityRouterOri(this);
	}

}
