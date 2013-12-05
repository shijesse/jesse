#!/usr/bin/python
ttlList = ['2','10','30','60','180','360','720','1440','4320']
settingsPath = 'Settings/CommunityRouter/config_ttl'
f = file('run.py','w')
f.write('#!/usr/bin/python\n')
f.write('import os\n')
for i in ttlList:
    f.write('os.system(\'./one.sh -b 1 '+settingsPath+i+'.txt\')\n')
f.close()
