from pathlib import Path

html_path = Path('duoapp/src/main/assets/index.html')
java_path = Path('duoapp/src/main/java/com/sergey/duochat/NativeRelayTransport.java')
main_path = Path('duoapp/src/main/java/com/sergey/duochat/MainActivity.java')
html = html_path.read_text(encoding='utf-8')
java = java_path.read_text(encoding='utf-8')
main = main_path.read_text(encoding='utf-8')

replacements = {
"""async function inviteDurak(peer){
 var old=Object.values(durakGames).find(function(g){return g.status==='active'&&durakPeer(g)===peer&&g.state.winner===null;});if(old){openDurak(old.id);return;}
 var id=randomId(),seed=randomSeed(),g={id:id,players:[role,peer],inviter:role,invitee:peer,status:'invited',updated:Date.now(),state:DurakRules.initial(seed),target:0};
 try{await publishEnvelope(peer,'game_invite',{gameType:'durak',gameId:id,seed:seed,state:g.state},4,'durak_invite',id);durakGames[id]=g;saveDurakGames();openDurak(id);}catch(e){toast(relayErrorText(e&&e.message||e));}
}""":
"""async function inviteDurak(peer){
 var old=Object.values(durakGames).find(function(g){return (g.status==='active'||g.status==='invited')&&durakPeer(g)===peer&&g.state.winner===null;});if(old){openDurak(old.id);return;}
 var id=randomId(),seed=randomSeed(),g={id:id,players:[role,peer],inviter:role,invitee:peer,status:'invited',updated:Date.now(),state:DurakRules.initial(seed),target:0};
 durakGames[id]=g;saveDurakGames();openDurak(id);
 try{await publishEnvelope(peer,'game_invite',{gameType:'durak',gameId:id,seed:seed,state:g.state},4,'durak_invite',id,true);}catch(e){toast('Приглашение сохранено. Повторите отправку позже.');}
}""",
"""async function acceptDurak(id){var g=durakGames[id];if(!g||g.invitee!==role||g.status!=='invited')return;try{await publishEnvelope(g.inviter,'game_accept',{gameType:'durak',gameId:id},4,'durak_accept',id);g.status='active';g.updated=Date.now();saveDurakGames();$('checkersRequest').classList.add('hidden');pendingDurakAcceptId='';openDurak(id);}catch(e){toast(relayErrorText(e&&e.message||e));}}""":
"""async function acceptDurak(id){var g=durakGames[id];if(!g||g.invitee!==role||g.status!=='invited')return;g.status='active';g.updated=Date.now();saveDurakGames();$('checkersRequest').classList.add('hidden');pendingDurakAcceptId='';openDurak(id);try{await publishEnvelope(g.inviter,'game_accept',{gameType:'durak',gameId:id},4,'durak_accept',id,true);}catch(e){toast('Ответ сохранён. Связь с соперником восстановится автоматически.');}}""",
"""async function declineDurak(id){var g=durakGames[id];if(!g)return;try{await publishEnvelope(g.inviter,'game_decline',{gameType:'durak',gameId:id},4,'game_decline',id);}catch(e){}g.status='declined';g.updated=Date.now();saveDurakGames();pendingDurakAcceptId='';$('checkersRequest').classList.add('hidden');}""":
"""async function declineDurak(id){var g=durakGames[id];if(!g)return;g.status='declined';g.updated=Date.now();saveDurakGames();pendingDurakAcceptId='';$('checkersRequest').classList.add('hidden');try{await publishEnvelope(g.inviter,'game_decline',{gameType:'durak',gameId:id},4,'game_decline',id,true);}catch(e){}}""",
"""async function sendDurakState(g,next){if(!next||durakSending)return;durakSending=true;try{await publishEnvelope(durakPeer(g),'game_move',{gameType:'durak',gameId:g.id,state:next,seq:next.seq},4,'durak_move',g.id);g.state=next;g.status=next.winner===null?'active':'ended';g.updated=Date.now();saveDurakGames();renderDurak();}catch(e){toast(relayErrorText(e&&e.message||e));}finally{durakSending=false;}}""":
"""async function sendDurakState(g,next){if(!next||durakSending)return;durakSending=true;g.state=next;g.status=next.winner===null?'active':'ended';g.updated=Date.now();saveDurakGames();renderDurak();try{await publishEnvelope(durakPeer(g),'game_move',{gameType:'durak',gameId:g.id,state:next,seq:next.seq},4,'durak_move',g.id,true);}catch(e){toast('Ход сохранён. Отправка продолжится в фоне.');}finally{durakSending=false;}}""",
"""async function resignDurak(){var g=durakGames[activeDurakId];if(!g||g.status!=='active'||!confirm('Сдаться в этой партии?'))return;var side=durakSide(g);try{await publishEnvelope(durakPeer(g),'game_resign',{gameType:'durak',gameId:g.id,seq:g.state.seq},4,'game_resign',g.id);}catch(e){toast(relayErrorText(e&&e.message||e));return;}g.state.winner=1-side;g.status='ended';g.updated=Date.now();saveDurakGames();awardGameOnce(g.id,'surrender');renderDurak();}""":
"""async function resignDurak(){var g=durakGames[activeDurakId];if(!g||g.status!=='active'||!confirm('Сдаться в этой партии?'))return;var side=durakSide(g);g.state.winner=1-side;g.status='ended';g.updated=Date.now();saveDurakGames();awardGameOnce(g.id,'surrender');renderDurak();try{await publishEnvelope(durakPeer(g),'game_resign',{gameType:'durak',gameId:g.id,seq:g.state.seq},4,'game_resign',g.id,true);}catch(e){toast('Результат сохранён. Отправка продолжится в фоне.');}}""",
}

for old, new in replacements.items():
    count = html.count(old)
    if count != 1:
        raise SystemExit(f'Expected exactly one Durak block, found {count}: {old[:60]}')
    html = html.replace(old, new)

# Home tunnels can need longer than 12 s to acknowledge a POST. Background game
# sends must not be killed while the relay is still committing the event.
java_old = 'c.setConnectTimeout(12000);c.setReadTimeout(12000);'
java_new = 'c.setConnectTimeout(20000);c.setReadTimeout(35000);'
if java.count(java_old) != 1:
    raise SystemExit('Batch timeout marker not found exactly once')
java = java.replace(java_old, java_new)
java_old2 = 'c.setConnectTimeout(12000);\n            c.setReadTimeout(12000);'
java_new2 = 'c.setConnectTimeout(20000);\n            c.setReadTimeout(35000);'
if java.count(java_old2) != 1:
    raise SystemExit('Single POST timeout marker not found exactly once')
java = java.replace(java_old2, java_new2)
java = java.replace('t.join(42000L);', 't.join(65000L);')

# loadDataWithBaseURL cannot fetch relative APK assets from https://app.local/.
# Checkers already worked because MainActivity inlined checkers.js. Inline durak.js
# the same way so DurakRules exists before the main application script runs.
main_old = '''            try (InputStream game = getAssets().open("checkers.js")) {
                ByteArrayOutputStream rules = new ByteArrayOutputStream();
                while ((n = game.read(buf)) > 0) rules.write(buf, 0, n);
                String marker = "<script src=\\\"checkers.js\\\"></script>";
                if (!html.contains(marker)) throw new IllegalStateException("checkers marker missing");
                html = html.replace(marker, "<script>\\n" +
                        new String(rules.toByteArray(), StandardCharsets.UTF_8) + "\\n</script>");
            }
'''
main_new = '''            String[] gameRuleAssets = {"checkers.js", "durak.js"};
            for (String assetName : gameRuleAssets) {
                try (InputStream game = getAssets().open(assetName)) {
                    ByteArrayOutputStream rules = new ByteArrayOutputStream();
                    while ((n = game.read(buf)) > 0) rules.write(buf, 0, n);
                    String marker = "<script src=\\\"" + assetName + "\\\"></script>";
                    if (!html.contains(marker)) throw new IllegalStateException(assetName + " marker missing");
                    html = html.replace(marker, "<script>\\n" +
                            new String(rules.toByteArray(), StandardCharsets.UTF_8) + "\\n</script>");
                }
            }
'''
if main.count(main_old) != 1:
    raise SystemExit('MainActivity game rules inline block not found exactly once')
main = main.replace(main_old, main_new)

html_path.write_text(html, encoding='utf-8')
java_path.write_text(java, encoding='utf-8')
main_path.write_text(main, encoding='utf-8')
print('Applied 6.0.20 home-relay/nonblocking Durak fix and inlined DurakRules')
