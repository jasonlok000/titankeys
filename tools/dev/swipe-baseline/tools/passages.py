"""Reads the reference app's prompted passages from its source, so the labels follow what was on screen."""
import re


def load(path):
    source = open(path, encoding='utf-8').read()
    normal = re.search(r'NORMAL\s*=\s*(.*?);', source, re.S).group(1)
    styles = re.search(r'STYLES\s*=\s*\{(.*?)\};', source, re.S).group(1)

    def text(fragment):
        return ''.join(re.findall(r'"((?:[^"\\]|\\.)*)"', fragment))

    # In STYLES each passage ends with a comma at the end of a Java string concatenation.
    parts = re.split(r'",\s*\n\s*\n', styles)
    return {'normal': text(normal), 'styles': [text(p + '"') for p in parts]}
