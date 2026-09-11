import os,sys
if os.environ.get('REGISTRY_GATE_OUTPUT') and sys.argv and os.path.basename(sys.argv[0]) in ('check_architecture.py','check_w5.py'):
 sys.path.insert(0,'/home/gustavo/workspace/teste-e2e/.w5-recovery/remediation')
 import observe_w5
