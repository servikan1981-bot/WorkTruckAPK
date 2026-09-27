from pathlib import Path
p=Path('duoapp/src/main/assets/index.html')
h=p.read_text()
h=h.replace("$('checkersAcceptBtn').addEventListener('click',function(){acceptCheckers(pendingGameAcceptId);});","$('checkersAcceptBtn').addEventListener('click',function(){acceptGame(pendingGameAcceptId);});")
h=h.replace("$('checkersDeclineBtn').addEventListener('click',function(){declineCheckers(pendingGameAcceptId);});","$('checkersDeclineBtn').addEventListener('click',function(){declineGame(pendingGameAcceptId);});")
h=h.replace("$('checkersAcceptBtn').onclick=function(){acceptGame(pendingGameAcceptId);};$('checkersDeclineBtn').onclick=function(){declineGame(pendingGameAcceptId);};\n","")
# Apply rating only when a completed game is subsequently rendered/received, not while an optimistic move is awaiting delivery.
h=h.replace("Object.values(games).forEach(function(g){applyGameResult(g);});localStorage.setItem('of620_games_'+ownTag", "localStorage.setItem('of620_games_'+ownTag")
h=h.replace("function renderGamesHub(){\n var r=loadRating()", "function renderGamesHub(){\n Object.values(games).forEach(function(g){applyGameResult(g);});\n var r=loadRating()")
# Remote completion is confirmed at receipt time.
h=h.replace("g.updated=Date.now();saveGames();renderHome();if(activeGameId===id)", "g.updated=Date.now();saveGames();if(g.status==='ended')applyGameResult(g);renderHome();if(activeGameId===id)")
p.write_text(h)
print('Applied 6.0.20 post-patch fixes')
