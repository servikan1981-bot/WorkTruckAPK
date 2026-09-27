(function(){
'use strict';
function syncGamesNav(){
  var nav=document.getElementById('ofBottom');
  if(!nav)return;
  var setup=document.getElementById('setup');
  var call=document.getElementById('callOverlay');
  var setupVisible=setup&&!setup.classList.contains('hidden');
  var callVisible=call&&!call.classList.contains('hidden');
  nav.style.display=(setupVisible||callVisible)?'none':'grid';
}
if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',function(){setTimeout(syncGamesNav,50);});
else setTimeout(syncGamesNav,50);
setInterval(syncGamesNav,500);
})();
