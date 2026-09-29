from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one anchor, found {count}")
    return text.replace(old, new, 1)

# This patch is applied after patch_family_630_games.py.  The first patch gives
# games a dedicated one-thread native executor.  Here we make every game send
# actually use that executor, and batch a multi-chunk encrypted Durak state into
# one HTTP POST so a whole move arrives atomically and quickly.

html = Path('duoapp/src/main/assets/index.html')
s = html.read_text(encoding='utf-8')

s = replace_once(
    s,
    "if(total>1&&total<=16&&/^(direct|group)_(offer|answer)$/.test(wk)&&AndroidBridge.sendRelayBatchAsync){",
    "if(total>1&&total<=16&&(/^(direct|group)_(offer|answer)$/.test(wk)||/^(durak_|game_)/.test(wk))&&AndroidBridge.sendRelayBatchAsync){",
    'game batch condition',
)

# Game traffic must not use the old durable outbox here: that outbox has four
# workers and can send adjacent state chunks out of order.  Urgent broadcasts
# keep using it. Game traffic now waits for the real native POST result.
s = replace_once(
    s,
    "var queued=nonBlocking&&/^(durak_|game_|urgent_broadcast$)/.test(wk);",
    "var queued=nonBlocking&&/^urgent_broadcast$/.test(wk);",
    'game queue bypass',
)

old_state = "async function sendDurakState(g,next){if(!next||durakSending)return;durakSending=true;g.state=next;g.status=next.winner===null?'active':'ended';g.updated=Date.now();saveDurakGames();renderDurak();try{await publishEnvelope(durakPeer(g),'game_move',{gameType:'durak',gameId:g.id,state:next,seq:next.seq},4,'durak_move',g.id,true);}catch(e){toast('Ход сохранён. Отправка продолжится в фоне.');}finally{durakSending=false;}}"
new_state = "async function sendDurakState(g,next){if(!next||durakSending)return;var previous=g.state,previousStatus=g.status;durakSending=true;g.state=next;g.status=next.winner===null?'active':'ended';g.updated=Date.now();saveDurakGames();renderDurak();try{await publishEnvelope(durakPeer(g),'game_move',{gameType:'durak',gameId:g.id,state:next,seq:next.seq},4,'durak_move',g.id,true);}catch(e){g.state=previous;g.status=previousStatus;g.updated=Date.now();saveDurakGames();renderDurak();toast('Ход не отправлен. Повторите ход.');}finally{durakSending=false;}}"
s = replace_once(s, old_state, new_state, 'Durak state rollback')

old_accept = "async function acceptDurak(id){var g=durakGames[id];if(!g||g.invitee!==role||g.status!=='invited')return;g.status='active';g.updated=Date.now();saveDurakGames();$('checkersRequest').classList.add('hidden');pendingDurakAcceptId='';openDurak(id);try{await publishEnvelope(g.inviter,'game_accept',{gameType:'durak',gameId:id},4,'durak_accept',id,true);}catch(e){toast('Ответ сохранён. Связь с соперником восстановится автоматически.');}}"
new_accept = "async function acceptDurak(id){var g=durakGames[id];if(!g||g.invitee!==role||g.status!=='invited')return;g.status='active';g.updated=Date.now();saveDurakGames();$('checkersRequest').classList.add('hidden');pendingDurakAcceptId='';openDurak(id);try{await publishEnvelope(g.inviter,'game_accept',{gameType:'durak',gameId:id},4,'durak_accept',id,true);}catch(e){g.status='invited';g.updated=Date.now();pendingDurakAcceptId=id;saveDurakGames();showDurakRequest();toast('Не удалось отправить принятие. Нажмите «Принять» ещё раз.');}}"
s = replace_once(s, old_accept, new_accept, 'Durak accept reliability')

old_resign = "async function resignDurak(){var g=durakGames[activeDurakId];if(!g||g.status!=='active'||!confirm('Сдаться в этой партии?'))return;var side=durakSide(g);g.state.winner=1-side;g.status='ended';g.updated=Date.now();saveDurakGames();awardGameOnce(g.id,'surrender');renderDurak();try{await publishEnvelope(durakPeer(g),'game_resign',{gameType:'durak',gameId:g.id,seq:g.state.seq},4,'game_resign',g.id,true);}catch(e){toast('Результат сохранён. Отправка продолжится в фоне.');}}"
new_resign = "async function resignDurak(){var g=durakGames[activeDurakId];if(!g||g.status!=='active'||!confirm('Сдаться в этой партии?'))return;var side=durakSide(g);try{await publishEnvelope(durakPeer(g),'game_resign',{gameType:'durak',gameId:g.id,seq:g.state.seq},4,'game_resign',g.id,true);g.state.winner=1-side;g.status='ended';g.updated=Date.now();saveDurakGames();awardGameOnce(g.id,'surrender');renderDurak();}catch(e){toast('Не удалось передать сдачу сопернику. Повторите.');}}"
s = replace_once(s, old_resign, new_resign, 'Durak resign reliability')

html.write_text(s, encoding='utf-8')

main = Path('duoapp/src/main/java/com/sergey/duochat/MainActivity.java')
s = main.read_text(encoding='utf-8')

old_batch = '''                android.content.Context app = getApplicationContext();
                CALL_POSTER.execute(() -> reportRelayResult(requestId,
                        NativeRelayTransport.postBatchJson(app, topic, messagesJson, priority)));
'''
new_batch = '''                android.content.Context app = getApplicationContext();
                String wireKind = relayBatchWireKind(messagesJson);
                ThreadPoolExecutor executor = isGameWireKind(wireKind) ? GAME_POSTER : CALL_POSTER;
                executor.execute(() -> reportRelayResult(requestId,
                        NativeRelayTransport.postBatchJson(app, topic, messagesJson, priority)));
'''
s = replace_once(s, old_batch, new_batch, 'game batch executor')

helper_anchor = '''        private boolean isGameWireKind(String kind) {
            return kind != null && (kind.startsWith("game_") || kind.startsWith("durak_"));
        }

'''
helper_new = helper_anchor + '''        private String relayBatchWireKind(String messagesJson) {
            try {
                org.json.JSONArray a = new org.json.JSONArray(messagesJson);
                return a.length() > 0 ? relayWireKind(a.optString(0, "")) : "";
            } catch (Exception ignored) { return ""; }
        }

'''
s = replace_once(s, helper_anchor, helper_new, 'batch kind helper')
main.write_text(s, encoding='utf-8')

print('6.0.30 ordered batched game transport applied')
