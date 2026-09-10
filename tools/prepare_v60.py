from pathlib import Path
import re

BLUE_ACCENT='Color.rgb(18,92,185)'
BLUE_DARK='Color.rgb(8,39,79)'

# Main application: compact 5-entry home screen, dark-blue corporate palette, no duplicate Tour tab.
p = Path('app/src/main/java/com/worktruck/catalog/V53Activity.java')
s = p.read_text(encoding='utf-8')

# Corporate palette.
s=s.replace('Color.rgb(8,91,58)', BLUE_ACCENT)
s=s.replace('Color.rgb(7,38,29)', BLUE_DARK)

# Keep bottom navigation to five unique destinations only.
s=s.replace('n.addView(nav("●","Профиль",4,this::showProfile),weight());n.addView(nav("◎","Тур",5,()->startActivity(new Intent(this,TourActivity.class))),weight());return n;',
            'n.addView(nav("●","Профиль",4,this::showProfile),weight());return n;')
s=s.replace('n.addView(nav("●","Профиль",4,this::showProfile),weight());return n;',
            'n.addView(nav("●","Профиль",4,this::showProfile),weight());return n;')

s = s.replace('postDelayed(this::showHome,550)', 'postDelayed(this::openStartScreen,420)')
if 'private void openStartScreen()' not in s:
    marker = '    private void showSplash(){'
    helper = '''    private void openStartScreen(){
        String screen=getIntent().getStringExtra("screen");
        if("request".equals(screen)){showRequest("");return;}
        if("orders".equals(screen)){showOrders();return;}
        if("profile".equals(screen)){showProfile();return;}
        showHome();
    }

'''
    if marker not in s: raise SystemExit('showSplash marker not found')
    s = s.replace(marker, helper + marker, 1)

home = '''    private void showHome(){
        prepare(0);
        ScrollView sv=new ScrollView(this);sv.setFillViewport(true);
        LinearLayout b=col();b.setPadding(dp(14),dp(10),dp(14),dp(18));
        b.addView(header());
        EditText search=searchField("Поиск по всей номенклатуре");
        search.setOnEditorActionListener((v,id,e)->{showProducts(null,search.getText().toString().trim());return true;});
        b.addView(search,lp(-1,dp(52),0,8,0,10));

        b.addView(hero("КАТАЛОГ","Поиск, наличие, цены и 10 разделов запчастей Scania","Открыть каталог",this::showCategories),lp(-1,dp(158),0,8,0,0));
        b.addView(hero("МОИ МАШИНЫ","Сохранённые Scania, VIN и госномера клиента","Открыть автопарк",()->{Intent i=new Intent(this,ClientHubActivity.class);i.putExtra("section","fleet");startActivity(i);}),lp(-1,dp(158),0,10,0,0));
        b.addView(hero("КЛИЕНТУ","Бонусы, ожидание деталей, статус заказа и персональный менеджер","Открыть сервисы",()->startActivity(new Intent(this,ClientHubActivity.class))),lp(-1,dp(158),0,10,0,0));
        b.addView(photoHero("НОВЫЕ ПОСТУПЛЕНИЯ","Автомобили Scania в разбор и новые позиции","Смотреть поступления",this::showVehicles),lp(-1,dp(178),0,10,0,0));
        b.addView(hero("ЕЩЁ","Экскурсия, игра и дополнительные возможности","Открыть ещё",()->startActivity(new Intent(this,MoreActivity.class))),lp(-1,dp(158),0,10,0,0));

        LinearLayout info=card();
        info.addView(tv(dbReady?"Каталог готов":"База номенклатуры недоступна",15,dbReady?GREEN:Color.rgb(170,80,0),true));
        info.addView(tv(dbReady?("В наличии: "+nf.format(database.getProductCount())+" позиций"):"Закройте приложение и откройте снова.",13,MUTED,false));
        b.addView(info,lp(-1,-2,0,14,0,0));
        sv.addView(b);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));root.addView(bottomNav());
    }

'''
pattern = r'    private void showHome\(\)\{.*?\n    private void showVehicles\(\)\{'
if not re.search(pattern, s, flags=re.S): raise SystemExit('showHome block not found')
s = re.sub(pattern, home + '    private void showVehicles(){', s, count=1, flags=re.S)
p.write_text(s, encoding='utf-8')

# Client hub: direct My cars from main; remove duplicate My fleet card from Client menu.
cp=Path('app/src/main/java/com/worktruck/catalog/ClientHubActivity.java')
c=cp.read_text(encoding='utf-8')
c=c.replace('Color.rgb(8,91,58)', BLUE_ACCENT).replace('Color.rgb(7,38,29)', BLUE_DARK)
if 'private boolean directSection=false;' not in c:
    c=c.replace('    private LinearLayout root;','    private LinearLayout root;\n    private boolean directSection=false;',1)
c=c.replace('        prefs=getSharedPreferences("wt_client_hub",MODE_PRIVATE);\n        showHub();',
'''        prefs=getSharedPreferences("wt_client_hub",MODE_PRIVATE);
        String section=getIntent().getStringExtra("section");
        directSection=section!=null&&!section.isEmpty();
        if("fleet".equals(section))showFleet();
        else if("bonus".equals(section))showBonus();
        else if("watch".equals(section))showWatch();
        else if("orders".equals(section))showOrderStatus();
        else if("manager".equals(section))showManager();
        else {directSection=false;showHub();}''',1)
if 'private void backFromSection()' not in c:
    c=c.replace('    private void showHub(){','    private void backFromSection(){if(directSection)finish();else showHub();}\n\n    private void showHub(){',1)
c=c.replace('        body.addView(menuCard("🚛","Мой автопарк","Сохраните свои Scania: модель, VIN и госномер",this::showFleet));\n','')
for a,b in [
('base("Мой автопарк",this::showHub);','base("Мой автопарк",this::backFromSection);'),
('base("Бонусный кошелёк",this::showHub);','base("Бонусный кошелёк",this::backFromSection);'),
('base("Сообщить о поступлении",this::showHub);','base("Сообщить о поступлении",this::backFromSection);'),
('base("Где мой заказ?",this::showHub);','base("Где мой заказ?",this::backFromSection);'),
('base("Персональный менеджер",this::showHub);','base("Персональный менеджер",this::backFromSection);')]: c=c.replace(a,b)
c=c.replace('@Override public void onBackPressed(){showHub();}','@Override public void onBackPressed(){if(directSection)finish();else showHub();}')
cp.write_text(c,encoding='utf-8')

# Other screens: corporate blue palette.
for rel in ['app/src/main/java/com/worktruck/catalog/GameActivity.java','app/src/main/java/com/worktruck/catalog/TourActivity.java','app/src/main/java/com/worktruck/catalog/MoreActivity.java']:
    q=Path(rel)
    if q.exists():
        t=q.read_text(encoding='utf-8').replace('Color.rgb(8,91,58)',BLUE_ACCENT).replace('Color.rgb(7,38,29)',BLUE_DARK)
        q.write_text(t,encoding='utf-8')

# Build-time assertions.
out=p.read_text(encoding='utf-8')
home_slice=out[out.index('private void showHome()'):out.index('private void showVehicles()')]
for token in ['hero("КАТАЛОГ"','hero("МОИ МАШИНЫ"','hero("КЛИЕНТУ"','photoHero("НОВЫЕ ПОСТУПЛЕНИЯ"','hero("ЕЩЁ"']:
    assert token in home_slice, token
assert '"Тур",5' not in out
assert 'dp(158)' in home_slice
cout=cp.read_text(encoding='utf-8')
assert '"fleet".equals(section)' in cout
assert 'menuCard("🚛","Мой автопарк"' not in cout
print('V6.1 BLUE UI + VISIBLE BUTTONS + DEDUPLICATED NAV CONFIRMED')
