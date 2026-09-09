#!/usr/bin/env python3
"""Basic syntax checker for Kotlin files - checks balanced braces, parens, brackets."""
import sys

def check_file(path):
    try:
        with open(path, 'r') as f:
            content = f.read()
        
        # Remove strings and comments (simplified)
        lines = content.split('\n')
        in_string = False
        in_comment = False
        cleaned = []
        for line in lines:
            if line.strip().startswith('//'):
                continue
            if '/*' in line:
                in_comment = True
            if '*/' in line:
                in_comment = False
                continue
            if in_comment:
                continue
            cleaned.append(line)
        
        cleaned_content = '\n'.join(cleaned)
        
        # Count braces
        open_braces = cleaned_content.count('{')
        close_braces = cleaned_content.count('}')
        open_parens = cleaned_content.count('(')
        close_parens = cleaned_content.count(')')
        open_brackets = cleaned_content.count('[')
        close_brackets = cleaned_content.count(']')
        
        issues = []
        if open_braces != close_braces:
            issues.append(f"Braces: {open_braces} open vs {close_braces} close")
        if open_parens != close_parens:
            issues.append(f"Parens: {open_parens} open vs {close_parens} close")
        if open_brackets != close_brackets:
            issues.append(f"Brackets: {open_brackets} open vs {close_brackets} close")
        
        if issues:
            print(f"  Issues: {', '.join(issues)}")
            return False
        return True
        
    except Exception as e:
        print(f"  Error: {e}")
        return False

if __name__ == '__main__':
    import subprocess
    result = subprocess.run(['git', 'diff', '--name-only'], capture_output=True, text=True, cwd='/home/ku7/git/nice_sudoku2')
    files = [f for f in result.stdout.strip().split('\n') if f.endswith('.kt')]
    
    print(f"Checking {len(files)} Kotlin files...")
    all_ok = True
    for f in files:
        print(f"  {f}", end=" ")
        if check_file(f'/home/ku7/git/nice_sudoku2/{f}'):
            print("OK")
        else:
            all_ok = False
    
    if all_ok:
        print("\nAll files pass basic syntax check!")
    else:
        print("\nSome files have issues!")
        sys.exit(1)