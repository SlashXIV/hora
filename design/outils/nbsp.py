"""Typographie française des strings.xml : espace insécable avant « : ; ! ? », dans
les guillemets et entre un nombre et son unité (h, min, jour). À lancer depuis la
racine du dépôt ; n'écrit que les fichiers qui changent, et les nomme."""
import glob
import re

N = ' '


def fix(match):
    text = match.group(2)
    text = re.sub(r' ([:;!?])', N + r'\1', text)
    text = text.replace('« ', '«' + N).replace(' »', N + '»')
    text = re.sub(r'(%\d?\$?d|\d) (h|min|jours?)\b', lambda m: m.group(1) + N + m.group(2), text)
    return match.group(1) + text + match.group(3)


for path in glob.glob('**/src/main/res/values/strings.xml', recursive=True):
    with open(path, encoding='utf-8') as f:
        before = f.read()
    after = re.sub(r'(<(?:string|item)[^>]*>)([^<]*)(</(?:string|item)>)', fix, before)
    if after != before:
        with open(path, 'w', encoding='utf-8') as f:
            f.write(after)
        print(path)
