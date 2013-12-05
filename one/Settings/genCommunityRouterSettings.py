#!/usr/bin/python
# -*- coding: cp936 -*-
import os
import shutil
TtlList = ['2','10','30','60','180','360','720','1440']
#生成文件夹，存放所有的配置文件,为数据集的名字
router_name = 'CommunityRouter'
fileDirName = router_name
traceFilePath = 'TraceData/infocom2006.txt'
scenario_name = router_name + '_'

updateInterval = '1'
#63,300
nodesNumber = '98'
#infocom2006syn :259199  infocom2006sim:333915
endTime = '337504'
bufferSize = '5M'
clique = '4'
thre = '10'
window = '21600'

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
	
    f.write('Report.reportDir = reports/'+router_name+'_'+clique+'_'+thre+'/'+i+'/'+'\n')
	
    f.write('Group.bufferSize = '+bufferSize+'\n')
    # BubbleRap
    f.write('CommunityRouter.window = '+window+'\n')
    f.write('CommunityRouter.entercommunity = '+thre+'\n')
    f.write('CommunityRouter.clique = '+clique+'\n')
    f.close()

    
    


