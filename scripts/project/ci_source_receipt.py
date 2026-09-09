#!/usr/bin/env python3
"""Record actual CI source checkout with local Git + event payload; no remote calls."""
from __future__ import annotations
import argparse
import json
import os
import re
import subprocess
from pathlib import Path

FIELDS = {'schema_version','event','workflow_run_id','source_head_sha','pr_head_sha',
          'pr_base_sha','event_checkout_sha','actual_checkout_sha','head_tree_sha',
          'checkout_tree_sha','checkout_parent_shas','classification'}


def classification(receipt: dict) -> str:
    if receipt['actual_checkout_sha'] == receipt['source_head_sha']:
        return 'EXACT_COMMIT_CHECKOUT'
    if receipt['head_tree_sha'] != receipt['checkout_tree_sha']:
        return 'DIFFERENT_TREE'
    if (receipt['event'] == 'pull_request' and receipt['actual_checkout_sha'] == receipt['event_checkout_sha'] and
            {receipt['pr_head_sha'],receipt['pr_base_sha']} <= set(receipt['checkout_parent_shas'])):
        return 'SYNTHETIC_MERGE_IDENTICAL_TREE'
    return 'OTHER_CHECKOUT_IDENTICAL_TREE'


def validate_receipt(receipt: dict, expected_head: str | None = None) -> list[str]:
    errors = []
    def require(ok, message):
        if not ok: errors.append('CI receipt: ' + message)
    try:
        require(set(receipt) == FIELDS and receipt['schema_version'] == 1, 'required evidence fields')
        require(receipt['event'] in {'pull_request','push'}, 'supported event')
        require(isinstance(receipt['workflow_run_id'], str) and receipt['workflow_run_id'].isdigit()
                and int(receipt['workflow_run_id']) > 0, 'workflow run ID')
        require(isinstance(receipt['checkout_parent_shas'],list) and all(isinstance(p,str) and re.fullmatch('[0-9a-f]{40}',p) for p in receipt['checkout_parent_shas']), 'checkout parents')
        for key in ['source_head_sha','event_checkout_sha','actual_checkout_sha','head_tree_sha','checkout_tree_sha']:
            require(isinstance(receipt[key],str) and re.fullmatch('[0-9a-f]{40}', receipt[key]) is not None, key)
        if receipt['event'] == 'pull_request':
            require(receipt['pr_head_sha'] == receipt['source_head_sha'], 'PR head correlation')
            require(isinstance(receipt['pr_base_sha'],str) and re.fullmatch('[0-9a-f]{40}',receipt['pr_base_sha']) is not None, 'PR base SHA')
        else:
            require(receipt['pr_head_sha'] is None and receipt['pr_base_sha'] is None, 'push payload is not PR base evidence')
        if expected_head is not None: require(receipt['source_head_sha'] == expected_head, 'stale source head')
        require(receipt['classification'] == classification(receipt), 'exact commit vs source tree relationship')
        if receipt['actual_checkout_sha'] == receipt['source_head_sha']:
            require(receipt['head_tree_sha'] == receipt['checkout_tree_sha'], 'same commit must have same tree')
    except (KeyError, TypeError, ValueError) as exc:
        errors.append('CI receipt: missing/invalid evidence: ' + str(exc))
    return errors


def collect(root: Path, event: dict, env: dict) -> dict:
    def git(ref):
        return subprocess.check_output(['git','rev-parse','--verify',ref],cwd=root,text=True).strip()
    kind = env['GITHUB_EVENT_NAME']
    pr = event['pull_request'] if kind == 'pull_request' else None
    source = pr['head']['sha'] if pr else event['after']
    # Only immutable validated hashes enter rev-parse; never use untrusted ref syntax.
    if not re.fullmatch('[0-9a-f]{40}', source): raise ValueError('invalid event source SHA')
    receipt = {'schema_version':1, 'event':kind,'workflow_run_id':env['GITHUB_RUN_ID'],
               'source_head_sha':source, 'pr_head_sha':source if pr else None,
               'pr_base_sha':pr['base']['sha'] if pr else None,
               'event_checkout_sha':env['GITHUB_SHA'], 'actual_checkout_sha':git('HEAD'),
               'head_tree_sha':git(source+'^{tree}'), 'checkout_tree_sha':git('HEAD^{tree}'),
               'checkout_parent_shas':subprocess.check_output(['git','show','-s','--format=%P','HEAD'],cwd=root,text=True).split()}
    receipt['classification'] = classification(receipt)
    errors = validate_receipt(receipt)
    if errors: raise ValueError('; '.join(errors))
    return receipt


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root',type=Path,default=Path(__file__).resolve().parents[2])
    parser.add_argument('--output',type=Path,required=True)
    args = parser.parse_args()
    receipt = collect(args.root, json.loads(Path(os.environ['GITHUB_EVENT_PATH']).read_text()), os.environ)
    encoded = json.dumps(receipt,indent=2)+'\n'
    args.output.parent.mkdir(parents=True,exist_ok=True)
    args.output.write_text(encoded)
    print(encoded,end='')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
