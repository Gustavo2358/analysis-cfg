"""Current W5 contract guards; 1.0 design witnesses are retained and checked as history."""
import hashlib,json,sys

METRICS=set('compositionRuns airReads airBytesObserved cfgBuilds defaultPlanDestinations defaultPlanQueries defaultPlanAssignVisits resultBytesWritten resultSha256Computed deliveryAttempts deliveryComplete deliveryFailures encodingFailures writeFailures finalizationFailures cleanupFailures'.split())
CHALLENGES={n:5 for n in 'drop-batch-dependency-reason-in-wire drop-candidate-support-in-wire swap-candidate-support-in-wire source-partial-promoted-to-exact-in-wire full-id-owner-loss wrong-result-id-receipt wrong-result-hash-receipt wrong-result-destination-receipt writer-failure-reported-complete partial-final-file-after-write-failure nondeterministic-result-order local-input-size-cap-used-as-semantic-admission resource-cap-on-result-facts-queries legacy-cfg-writer-used-for-analysis-result golden-substituted-for-real-stage'.split()}
MODULES={'analysis-dataflow':dict(wave=5,direct=['cfg-kernel','analysis-kernel','analysis-values','air-java'],forbidden=['analysis-adapters','analysis-launcher','air-json']),
 'analysis-adapters':dict(wave=5,direct=['analysis-dataflow','analysis-kernel','analysis-values','air-java','air-json'],forbidden=['analysis-launcher','cfg-adapters','cfg-launcher']),
 'analysis-launcher':dict(wave=5,direct=['analysis-dataflow','analysis-adapters','analysis-kernel','air-java','air-json'],forbidden=['cfg-launcher'])}
def validate(root):
    errors=[]
    def require(ok,reason):
        if not ok:errors.append('W5: '+reason)
    def read(path):return json.loads((root/path).read_text())
    try:
        sys.path.insert(0,str(root/'scripts/project'))
        from result_wire import read_result
        from check_w5 import verify_sources
        verify_sources(root)
        contract=read('docs/evals/cp5/result-contract.json');historical=read('docs/evals/cp5/history/result-contract.json')
        require(contract['status']=='IMPLEMENTED_W5' and contract['schema']=='analysis-dataflow-result' and contract['version']=='1.1.0','production result schema/version')
        require(contract['prepared_schema']=='prepared-analysis-result' and contract['prepared_version']=='1.1.0' and contract['receipt_schema']=='analysis-delivery-receipt' and contract['receipt_version']=='1.0.0','production envelope/receipt versions')
        require(set(contract['required_result_fields'])==set(historical['required_result_fields'])|{'analysisReason'},'lossless result fields')
        require(set(contract['required_observation_fields'])==set(historical['required_observation_fields'])|{'candidateSupports'},'lossless observation fields')
        require(set(contract['completion']['prepared_fields'])==set(historical['completion']['prepared_fields'])|{'analyses','planningEpoch','statistics'},'prepared fields')
        for key in ('query_statuses','unsupported_point_null_fields','execution_statuses','value_fields','analysis_key_options'):
            require(contract[key]==historical[key],'preserved wire semantics: '+key)
        require(contract['candidate_support_fields']==['candidate','producers'] and contract['producer_fields']==['evidence','origin','premiseRefs'],'candidate/producer binding fields')
        require(contract['fact_fields']==['kind','sequenceId','observationBatchId','query'],'generic fact contract')
        require(contract['source_scope_fields']==['scope','publicationInventory','unitInventory','entryUncertaintyRefs','remainderPolicy'],'source scope contract')
        for field in ('encoding','version_decision','writer','reader'):require(bool(contract[field]),'explicit version/encoding decision')
        review=read('docs/evals/cp5/result-review.json');require(review['kind']=='EXECUTED_W5_SNAPSHOT','current review must be executed W5')
        path=root/review['result_file'];require(hashlib.sha256(path.read_bytes()).hexdigest()==review['result_sha256'],'executed snapshot hash')
        read_result(path)
        phase=read('docs/evals/cp5/phase-review.json');require(phase['kind']=='IMPLEMENTED_W5_PHASE_CONTRACT','current phase review')
        partial=root/phase['executed_partial_snapshot'];require(hashlib.sha256(partial.read_bytes()).hexdigest()==phase['partial_snapshot_sha256'],'partial snapshot hash');read_result(partial)
        require(contract['completion']['observation_not_started_reasons']==['DEPENDENCY_UNAVAILABLE'],'explicit unavailable batch reason')
        require(read('docs/evals/cp5/architecture.json')['compatibility_composition_inventory']=='docs/evals/resource-limit-w5-inventory.json','focal compatibility bytecode inventory')
        require(read('docs/evals/cp5/architecture.json')['composition_inventory']=='docs/evals/cp5/w5-inventory.json','W5 bytecode inventory')
        require(read('docs/evals/cp5/metrics.json')['wave_5']['hook']=='scripts/project/check_w5.py','W5 metrics hook')
    except Exception as e:errors.append('W5 contract: '+str(e))
    return errors
