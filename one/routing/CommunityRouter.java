package routing;
import core.Connection;
import core.DTNHost;
import core.Message;
import core.Settings;
import core.SimClock;
import core.Tuple;

import java.util.*;
/**
 * ��������ָ��·��ת��
 * ��Panhui�ķֲ�ʽ���ŷ����㷨���иĽ�������aging���ƣ���������
 * @author Jesse
 *2013.3.31
 */
public class CommunityRouter extends ActiveRouter{

	/** Community router's setting namespace ({@value})*/
	public static final String COMMUNITY_NS = "CommunityRouter";
	
	/** ����k-clique �㷨��kֵ*/
	public static final String CLIQUE_VALUE = "clique";
	
	/** ���ýڵ�������ŵ���ֵ Ϊ��������*/
	public static final String ENTER_COMMUNITY_THRE = "entercommunity";

	/** ���û������ڵ���ֵ Ĭ��Ϊ6Сʱ*/
	public static final String WINDOW_SIZE = "window";

	private int cliqueValue;   // the k value of the k-clique	
	private int communityThre;   // �������ŵ���ֵ��Ϊ��������
	private int windowSize;   // �������ڵĴ�С
	/**��¼�������ڵ��ڻ��������ڵ���������*/
	private Map<DTNHost, Double> recordNodesTimes;
	/** ��¼����ʱ��*/
	private Map<DTNHost, LinkedList<Double>> recordEncounterTimes;
	/**��¼�������ڲ��ڵ�ľ��� ���¹����������ŵĸ��¶����� <nodeNum,dis>*/
	private Set<DTNHost> communitySet;   // ��¼����
	private Set<DTNHost> familaritySet;   // ��¼��Ϥ��
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
		// �������ļ��ж�ȡһЩ���õ���ֵ
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
		// ���Է���˽�еĳ�Ա������
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
	 * �൱��DisComDet��Node���е�updateTimes����
	 * ������һ���ڵ��ʱ�򣬸ú�������һ���ڵ���뵽�ýڵ������б���
	 * �����º����������ڵ��ڻ��������ڵ��������� 
	 * Ҫ��Ҫ�������ڵĽڵ���и��£�
	 * @param host
	 */
	public void updateMeetTimes(DTNHost host){
		double currentTime = SimClock.getTime();   // current time
		if(recordEncounterTimes.get(host) == null){
			recordEncounterTimes.put(host,new LinkedList<Double>());
		}
		recordEncounterTimes.get(host).addLast(currentTime);
		// ע���һ�����ӵ����  beginTimeΪ�ڵ�Լ����罨�����ӵ�ʱ�� Ӧ�ö����ӵ�ÿһ���ڵ���м��
		for(Map.Entry<DTNHost, LinkedList<Double>> entry :recordEncounterTimes.entrySet()){
			DTNHost keyValue = entry.getKey();
			if(recordEncounterTimes.get(keyValue).isEmpty()) // ����ڵ� ֱ��Ϊ��
				continue;
			double beginTime = recordEncounterTimes.get(keyValue).getFirst();
			int deleteNum = 0;
			while(currentTime - beginTime > windowSize){
				recordEncounterTimes.get(keyValue).removeFirst();
				deleteNum++;
				if(recordEncounterTimes.get(keyValue).isEmpty()) // ����ڵ� ֱ��Ϊ��
					break;
				beginTime = recordEncounterTimes.get(keyValue).getFirst();
			}
			
			recordNodesTimes.put(keyValue, recordNodesTimes.get(keyValue) == null
					?0:recordNodesTimes.get(keyValue)-deleteNum);
		}	
		// �ڵ�Լ��ϴ��ۻ���������
		double counts = recordNodesTimes.get(host) == null?0:recordNodesTimes.get(host);
		recordNodesTimes.put(host, counts+1);
	}
	/**
	 * ��Ӧ��DisComDet�е�exchangeMessage
	 * @param host ��������һ���ڵ�
	 */
	public void exchangeCommunity(DTNHost host){
		// ��ʹ���ۻ�ʱ����Ϊ��ֵ�ı�׼����Ӧ�ÿ����������ʱ�� ���ǽ��ڵ㳤ʱ�䲻�������Ӽ������Ƴ���
		double time = recordNodesTimes.get(host) == null?0:recordNodesTimes.get(host);
		if(time > communityThre){   //�ֲ�ʽ���ŷ��ֲ���1
			familaritySet.add(host);
			communitySet.add(host);
			fsolc.put(host, ((CommunityRouter)host.getRouter()).getFamilaritySet());
			mergeTwoCommunity(host,cliqueValue);   // ����3  ���ڵ��������������м�������ʱ���ϲ���������,
		}else{   //����2
			// �����ڵ��familarity set �� ���ڵ��community�Ľ���   �������ڵ��familarity set�Ľ�����
			int intersectionSize = calcTwoSetIntersection(communitySet, ((CommunityRouter)
					host.getRouter()).getFamilaritySet());
			if(intersectionSize > cliqueValue-2){ //k-clique
				communitySet.add(host);
				fsolc.put(host, ((CommunityRouter)host.getRouter()).getFamilaritySet());   // another.getFamilaritySet()
				// ����3  ���ڵ��������������м�������ʱ���ϲ���������
				mergeTwoCommunity(host,cliqueValue);
			}
		}
		// �Ӽ�����ɾ����������ʱ�������Ӵ���С����ֵ�Ľڵ�
		deleteNodeInFamilarityAndCommuSet(SimClock.getTime());
	}
	
	/**
	 * ��familaritySet�е�ÿһ���ڵ㣬��������recordEncounterTimes��������ʱ�̣����
	 * ��ʱ�̳����������ڵķ�Χ�����familaritySet��ɾ���ýڵ� ��������ɾ��
	 * Ӧ�ôӼ��������෴�ĽǶȿ���ɾ�������ڵĽڵ�
	 * @param currentTime
	 */
	public void deleteNodeInFamilarityAndCommuSet(double currentTime){
		// ���������еĽڵ�
		for(Iterator<DTNHost> itr = familaritySet.iterator(); itr.hasNext();){
			DTNHost host = itr.next();
			if(recordEncounterTimes.get(host).isEmpty())   // ���������б��� ɾ��
				itr.remove();
			else if(this.recordNodesTimes.get(host) < this.communityThre){
				itr.remove();   //С�ڽ�����Ϥ������ֵ��ɾ��.  2013.4.15
			}
		}
		// ɾ�������еĽڵ�
		for(Iterator<DTNHost> itr = communitySet.iterator(); itr.hasNext();){
			DTNHost host = itr.next();
			if(recordNodesTimes.get(host) == null){
				itr.remove();
			}
			else if(recordNodesTimes.get(host) > this.communityThre){   // ֱ�Ӽ�������
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
	
	// ���ݽڵ�ŵõ�fsolc�е�һ��^Fi
	public Set<DTNHost> getFsolc(DTNHost host){
		if(fsolc.containsKey(host))
			return fsolc.get(host);
		else{
			// ��ν��д�����
			return null;
		}	
	}
	
	public Set<DTNHost> getCommunitySet(){
		return communitySet;
	}
	
	/**
	 * �ϲ��������ţ����ڷֲ�ʽ���ŷ����㷨�Ĳ���3
	 * ������һ���ڵ������ŵ�ÿһ���㣬��ÿһ����ά����ÿһ����Ϥ����
	 * ����ü��Ϻͱ��ڵ����ŵĽ�����������k-1�����뵱ǰ�ڵ������
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
	 * �����������ϵĽ���
	 * @param first
	 * @param second
	 * @return �����Ĵ�С
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
				}   // otrRouter.distance() > distance() ʹ�þ����������
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
	 * �жϱ��ڵ㣬�м�ڵ��Ŀ�Ľڵ�֮��ľ��� ���ݾ����ж��Ƿ�ת�����м�ڵ�
	 * ����ret����ֵ�����Ϊfalse����ɷ���ʵ���������������Ϊһ����
	 * @param byRouter
	 * @param destination
	 * @return
	 */
	private boolean isForward(CommunityRouter byRouter, DTNHost destination){
		boolean ret = false;
		
		if(isInSameComm(byRouter, destination)){
			// �м�ڵ���Ŀ�Ľڵ���� getDistance��������
			if(this.getDistance(destination) > byRouter.getDistance(destination))
				ret = true;
		}   // �����ڵ���ͬһ������
		else if(this.isInSameComm(byRouter.getHost()) && byRouter.isInSameComm(destination)){
			ret = true;
		}   // �����ڵ����������� �м�ڵ����Žڵ�
		else{   // Ŀ�Ľڵ���ͼΪ��
			if(this.comDis(destination) < byRouter.comDis(destination))   // ֵ���� ˵����ͬ�ڵ����
				ret = true;
		}// �����ڵ�����������
		ret = false;   //
		return ret;
	}
	
	/**
	 * �ж��Ƿ����ת�����Զ����ж�Ŀ�Ľڵ��Ƿ��ڵ�ǰ�ڵ�������ڣ��Լ������ڲ��ڵ��������
	 * ���ڣ�������ת����
	 * �����ж��м�ڵ��Ƿ��ڵ�ǰ�ڵ�������ڣ������٣�ת�����м�ڵ㣬�ñ����л���ת�������š�
	 * 
	 * ʵ����˵��������̫�࣬Ӧ�ò��õ�һ��ת���㷨������
	 * ʵ�鷽������ֵ���õ�Сһ�㣬�ø���Ľڵ��������� 
	 * ���߲����Լ��޸ĺ�����ŷ����㷨��ʹ��ԭʼPanHui���ŷ����㷨
	 * @param byRouter
	 * @param destination
	 * @return
	 */
	private boolean isForward2(CommunityRouter byRouter, DTNHost destination){
		// boolean ret = false;
		if(isInSameComm(destination)){   // Ŀ�Ľڵ�͵�ǰ�ڵ���ͬһ�����ţ�������ת��
			return false;
		}
		if(byRouter.isInSameComm(destination)){   // �ж�Ŀ�Ľڵ��Ƿ����м�ڵ�������� ���ڣ�ת�����м�ڵ�
			return true; 	
		}
		if(isInFamiloSet(destination)){   // �ж��Ƿ��ڵ�ǰ�ڵ����ڵ������ڵĽڵ�������ڣ����ڣ���ת�����м�ڵ�
			return false;
		}
		if(isInSameComm(byRouter.getHost()))   // �ж��м�ڵ��Ƿ��ڵ�ǰ�ڵ�������ڣ����ڣ���Ҫת��
			return false;
		if(byRouter.isInFamiloSet(destination)){   // �ж��Ƿ����м�ڵ������ڽڵ�������ڣ����ڽ���ת��
			return true;
		}
			// return true;   // �����ٵ�ǰ�ڵ������ڵı���ת����ȥ���л�������Ŀ�Ľڵ� ����������
		// ̫�࣬��Ϊ������
		return false;   // ƽ������Ϊһ
	} 

	
	/**
	 * �������ڵ㲻��һ�����ţ�����ýڵ��������ź���һ���ڵ��������ŵľ���
	 * �þ���ʹ���������ż���ͬ�ڵ������ ��ֵԽ��˵������Խ��
	 * @param anotherNode
	 * @return �����������н���ʱ�����ؽ����Ĵ�С�����򷵻�Integer.MAX_VALUE
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
	 * �������ڵ�λ��ͬһ������ʱ�����������ڵ��ľ���
	 * �þ���ʹ����������Ϊ����Ϥ���еõ��Ľڵ�����Ϊ1
	 * �������ڵ㴦�õ��Ľڵ�����Ϊ2
	 * @param anothreNode
	 * @return ͨ��ֵΪ1����2��������ֵΪInteger.MAX_VALUE��˵������ͬһ�������ڡ�
	 */
	public int nodeDis(DTNHost anothreNode){
		int dis = Integer.MAX_VALUE;
//		if(distance.containsKey(anothreNode))
//			dis = distance.get(anothreNode);
		return dis;
	}
	
	/**
	 * ��������һ���ڵ�ʱ����������룬�ϲ��ڵ����������ż���� ��DisComDet�е�dist����
	 * ���ȣ�����ͬһ�������ڵĽڵ㣬��������
	 * ֮�����ǲ�ͬ���ŵĽڵ㣬������ͬ�ڵ������
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
			return dis;   // ���ż�ľ������һ��base�ͽڵ��ľ����������
		}
		//return dis;
	}
	
	/**
	 * �ж������ڵ��Ƿ���ͬһ������ ʹ��һ���ڵ��Ƿ�����һ���ڵ��������
	 * ������� ��һ�������ڲ��Ľڵ����ʱ������Ϥ���еĲ��ֽڵ���뵽������
	 * @param anotherNode
	 * @return ����ͬһ������ ����ture ����false
	 */
	public boolean isInSameComm(DTNHost anotherNode){
		boolean ret = false;
		if(communitySet.size() < 3){   // �������еĽڵ���� �������5���ڵ���Ϊ�����еĽڵ�
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
				// �����б��ǰ5���������ڵ㣬��Ϊ�������ڵĽڵ�
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
	 * �ж������ڵ��Ƿ���ͬһ������
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
	 *  �ж���һ���ڵ㣨Ŀ�Ľڵ㣩�Ƿ��ڵ�ǰ�ڵ������ڽڵ��������
	 *  �������е�ÿһ���ڵ㣬����Familarity set of local community
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
	 * ʵ�ֵ�����
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
