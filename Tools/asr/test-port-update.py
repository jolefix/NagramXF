#!/usr/bin/env python3
"""Exercise successful ports, conflicts and preservation of local changes using local Git fixtures."""
from pathlib import Path
import subprocess
import tempfile

script = Path(__file__).with_name('port-update.sh').read_text()

def git(cwd, *args, check=True):
    return subprocess.run(['git', '-C', str(cwd), *args], text=True,
                          stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=check)

def commit(repo, message):
    git(repo, 'add', '.')
    git(repo, 'commit', '-m', message)
    return git(repo, 'rev-parse', 'HEAD').stdout.strip()

with tempfile.TemporaryDirectory() as temp:
    root = Path(temp)
    remote, seed = root/'origin.git', root/'seed'
    subprocess.run(['git', 'init', '--bare', str(remote)], check=True, capture_output=True)
    subprocess.run(['git', 'init', '-b', 'dev', str(seed)], check=True, capture_output=True)
    git(seed, 'config', 'user.name', 'Test')
    git(seed, 'config', 'user.email', 'test@example.invalid')
    (seed/'code').write_text('base\n')
    base = commit(seed, 'base')
    git(seed, 'remote', 'add', 'origin', str(remote))
    git(seed, 'push', 'origin', 'dev')
    git(seed, 'switch', '-c', 'feature/audio-transcriptions')
    (seed/'code').write_text('ASR\n')
    feature = commit(seed, 'feature')
    (seed/'tools').write_text('maintenance\n')
    commit(seed, 'tools')
    git(seed, 'push', 'origin', 'feature/audio-transcriptions')
    git(seed, 'switch', 'dev')
    (seed/'upstream').write_text('new version\n')
    upstream = commit(seed, 'upstream')
    git(seed, 'push', 'origin', 'dev')
    tested_script = root/'port.sh'
    tested_script.write_text(script.replace('c31a2ba1ab1134ea1a423bb401899b53e5310bb6', base))

    def checkout(name):
        repo = root/name
        subprocess.run(['git', 'clone', '-b', 'dev', str(remote), str(repo)], check=True, capture_output=True)
        git(repo, 'config', 'user.name', 'Test')
        git(repo, 'config', 'user.email', 'test@example.invalid')
        return repo

    clean = checkout('clean')
    result = subprocess.run(['bash', str(tested_script), 'dev', 'asr/new'], cwd=clean, capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    assert (clean/'code').read_text() == 'ASR\n'
    assert (clean/'upstream').read_text() == 'new version\n'
    assert (clean/'tools').exists()
    assert git(clean, 'rev-list', '--count', upstream+'..HEAD').stdout.strip() == '2'
    assert git(clean, 'ls-remote', '--heads', 'origin', 'asr/new').stdout == ''  # never pushes
    print('Successful port and no automatic push: passed')

    (clean/'code').write_text('local changes\n')
    before = git(clean, 'rev-parse', 'HEAD').stdout
    result = subprocess.run(['bash', str(tested_script), 'dev', 'asr/dirty'], cwd=clean, capture_output=True)
    assert result.returncode != 0
    assert git(clean, 'rev-parse', 'HEAD').stdout == before
    assert (clean/'code').read_text() == 'local changes\n'
    print('Local changes preserved: passed')

    (seed/'code').write_text('upstream conflicting edit\n')
    commit(seed, 'conflict')
    git(seed, 'push', 'origin', 'dev')
    conflict = checkout('conflict')
    result = subprocess.run(['bash', str(tested_script), 'dev', 'asr/conflict'], cwd=conflict, capture_output=True, text=True)
    assert result.returncode != 0
    assert git(conflict, 'rev-parse', '--verify', 'CHERRY_PICK_HEAD').stdout.strip() == feature
    assert 'UU code' in git(conflict, 'status', '--porcelain').stdout
    git(conflict, 'cherry-pick', '--abort')
    assert (conflict/'code').read_text() == 'upstream conflicting edit\n'
    print('Conflict stops safely and can be aborted: passed')
