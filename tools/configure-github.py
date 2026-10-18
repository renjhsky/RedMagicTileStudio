#!/usr/bin/env python3
"""Bind the in-app update entry to a real GitHub repository. No credentials required."""
import argparse
from pathlib import Path
import re

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('repository',help='Actual owner/repository, for example account/RedMagicTileStudio')
    args=parser.parse_args()
    if not re.fullmatch(r'[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})/[A-Za-z0-9._-]+',args.repository):
        parser.error('Expected owner/repository, without a URL, query, or access token.')
    root=Path(__file__).resolve().parents[1]
    (root / 'repository.properties').write_text('githubRepository='+args.repository+'\n',encoding='utf-8')
    print('Update entry: https://github.com/'+args.repository+'/releases')

if __name__=='__main__':
    main()
