from pathlib import Path

p = Path('duoapp/src/main/java/com/sergey/duochat/MessagingService.java')
s = p.read_text(encoding='utf-8')
old = 'notifyUrgent(eventTime > 0L ? eventTime : System.currentTimeMillis(), eventId);'
new = 'notifyUrgent(eventTime > 0L ? eventTime : System.currentTimeMillis(), id);'
if s.count(old) != 1:
    raise SystemExit(f'urgent event id marker count={s.count(old)}')
p.write_text(s.replace(old, new), encoding='utf-8')
print('Fixed 6.0.21 urgent notification event id')
