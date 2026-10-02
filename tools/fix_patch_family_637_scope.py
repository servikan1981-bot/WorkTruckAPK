from pathlib import Path

p = Path('tools/patch_family_637_media.py')
s = p.read_text(encoding='utf-8')
end_marker = '    "renderMessages attachment reuse",\n)\n'
end = s.index(end_marker) + len(end_marker)
start = s.rfind('h = once(\n    h,\n', 0, end)
if start < 0:
    raise SystemExit('renderMessages replacement block not found')
replacement = '''start = h.index("function renderMessages(){")
end = h.index("function openPicker(", start)
block = h[start:end]
block = once(
    block,
    "   if(m.attachment)renderAttachmentInto(b,m.attachment);",
    "   if(m.attachment){var attachmentKey=attachmentCacheKey(m.attachment),kept=keptAttachments[attachmentKey];if(kept){b.appendChild(kept);delete keptAttachments[attachmentKey];}else renderAttachmentInto(b,m.attachment);}",
    "renderMessages attachment reuse",
)
h = h[:start] + block + h[end:]
'''
s = s[:start] + replacement + s[end:]
p.write_text(s, encoding='utf-8')
