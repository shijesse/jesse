#!/usr/bin/python
# -*- coding: cp936 -*-
import os
import shutil
TtlList = ['2','10','30','60','180','360','720','1440','4320']
#生成文件夹，存放所有的配置文件,为数据集的名字
router_name = 'DecisionEngineRouter'
fileDirName = router_name+'Settings'
traceFilePath = 'TraceData/infocom2006.txt'
scenario_name = router_name + '_'

updateInterval = '1'
#63,300
nodesNumber = '98'
#infocom2006syn :259199  infocom2006sim:333915
endTime = '337504'
bufferSize = '5M'
clique = '8'
thre = '15000'
engine = 'community.DistributedBubbleRap'
#window = '216000'

if os.path.exists(fileDirName):
    #delete the folder
    print 'exits folder'+fileDirName
    shutil.rmtree(fileDirName)
os.mkdir(fileDirName)

for i in TtlList:
    f = file(fileDirName+'/' + 'config_ttl' +i+'.txt','w')
    #infocom2006sim_individual,infocom2006sim_social,
    #infocom2006syn_individual,infocom2006syn_social
    f.write('Scenario.name = '+scenario_name+i+'\n')
    f.write('Group.router = '+router_name+'\n')
    f.write('Scenario.endTime = '+endTime+'\n')
	
    f.write('Scenario.updateInterval = '+updateInterval+'\n')
	
    f.write('Group.nrofHosts = '+nodesNumber+'\n')
    f.write('Events1.hosts = 0,'+nodesNumber+'\n')
    f.write('Group.msgTtl = '+i+'\n')\
	
    f.write('Events2.filePath = '+traceFilePath+'\n')
	
    f.write('Report.reportDir = reports/'+fileDirName+'_'+clique+'_'+thre+'/'+i+'/'+'\n')
	
    f.write('Group.bufferSize = '+bufferSize+'\n')
    # BubbleRap
    f.write('DecisionEngineRouter.decisionEngine = '+engine+'\n')
    f.write('DecisionEngineRouter.communityDetectAlg = routing.community.KCliqueCommunityDetection\n')
    f.write('DecisionEngineRouter.K = '+clique+'\n')
    f.write('DecisionEngineRouter.familiarThreshold = '+thre+'\n')
    f.write('DecisionEngineRouter.centralityAlg = routing.community.SWindowCentrality\n')
    f.close()

    
    


