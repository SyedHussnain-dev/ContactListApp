package com.example.pulsedialer;

import android.Manifest;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.telecom.TelecomManager;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import android.content.res.ColorStateList;
import androidx.fragment.app.Fragment;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {
    private int selectedTab=R.id.nav_recents; private BottomNavigationView nav;
    private ActivityResultLauncher<String> permissionRequest;
    private ActivityResultLauncher<Intent> roleRequest;
    public static boolean hasPermission(Context c,String p){return ContextCompat.checkSelfPermission(c,p)==PackageManager.PERMISSION_GRANTED;}
    public static boolean isDefaultDialer(Context c){if(Build.VERSION.SDK_INT>=29){RoleManager r=c.getSystemService(RoleManager.class);return r!=null&&r.isRoleHeld(RoleManager.ROLE_DIALER);}TelecomManager t=(TelecomManager)c.getSystemService(TELECOM_SERVICE);return t!=null&&c.getPackageName().equals(t.getDefaultDialerPackage());}
    @Override protected void onCreate(Bundle state){super.onCreate(state);
        permissionRequest=registerForActivityResult(new ActivityResultContracts.RequestPermission(),granted->{if(!granted)Toast.makeText(this,R.string.permission_denied,Toast.LENGTH_SHORT).show();Fragment visible=getSupportFragmentManager().findFragmentById(R.id.content);if(visible instanceof DialpadFragment)((DialpadFragment)visible).onPermissionChanged();else showTab(selectedTab);});
        roleRequest=registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),r->{showTab(selectedTab);if(isDefaultDialer(this)&&Build.VERSION.SDK_INT>=33&&!hasPermission(this,Manifest.permission.POST_NOTIFICATIONS))permissionRequest.launch(Manifest.permission.POST_NOTIFICATIONS);});
        applyTheme();setContentView(R.layout.activity_main);WindowCompat.setDecorFitsSystemWindows(getWindow(),false);ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root), (view,insets)->{androidx.core.graphics.Insets bars=insets.getInsets(WindowInsetsCompat.Type.systemBars());view.setPadding(0,bars.top,0,bars.bottom);return insets;});applySystemBarAppearance();MaterialToolbar bar=findViewById(R.id.toolbar);setSupportActionBar(bar);
        nav=findViewById(R.id.navigation);applyNavigationAccent();nav.setOnItemSelectedListener(item->{showTab(item.getItemId());return true;});
        if(state!=null)selectedTab=state.getInt("tab",R.id.nav_recents);
        nav.setSelectedItemId(selectedTab);
        Intent intent=getIntent();if(Intent.ACTION_DIAL.equals(intent.getAction())){Uri data=intent.getData();openDialpad(data==null?"":data.getSchemeSpecificPart());}
    }
    private void applyTheme(){String value=getPreferences(0).getString("theme","dark");AppCompatDelegate.setDefaultNightMode("light".equals(value)?AppCompatDelegate.MODE_NIGHT_NO:"system".equals(value)?AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM:AppCompatDelegate.MODE_NIGHT_YES);}
    private void applySystemBarAppearance(){boolean light=(getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)!=Configuration.UI_MODE_NIGHT_YES;WindowInsetsControllerCompat controller=WindowCompat.getInsetsController(getWindow(),getWindow().getDecorView());controller.setAppearanceLightStatusBars(light);controller.setAppearanceLightNavigationBars(light);}
    private int accentColor(){return getPreferences(0).getInt("accent",0xFF3B82F6);}
    private void applyNavigationAccent(){if(nav==null)return;int accent=accentColor();ColorStateList tint=new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{accent,getColor(R.color.pulse_on_surface)});nav.setItemIconTintList(tint);nav.setItemTextColor(tint);}
    private void showTab(int id){selectedTab=id;Fragment f=ListFragment.newInstance(id);getSupportFragmentManager().beginTransaction().replace(R.id.content,f,"tab_"+id).commit();invalidateOptionsMenu();}
    public void requestContactAccess(){if(!hasPermission(this,Manifest.permission.READ_CONTACTS))permissionRequest.launch(Manifest.permission.READ_CONTACTS);else showTab(R.id.nav_contacts);}
    public void requestContactWriteAccess(){if(!hasPermission(this,Manifest.permission.WRITE_CONTACTS))permissionRequest.launch(Manifest.permission.WRITE_CONTACTS);}
    public void requestPhoneStateAccess(){if(!hasPermission(this,Manifest.permission.READ_PHONE_STATE))permissionRequest.launch(Manifest.permission.READ_PHONE_STATE);}
    public void requestCallLogAccess(){if(!isDefaultDialer(this)){requestDefaultRole();return;}if(!hasPermission(this,Manifest.permission.READ_CALL_LOG))permissionRequest.launch(Manifest.permission.READ_CALL_LOG);else showTab(R.id.nav_recents);}
    public void openDialpad(String number){DialpadFragment f=DialpadFragment.newInstance(number);getSupportFragmentManager().beginTransaction().replace(R.id.content,f).addToBackStack("dialpad").commit();}
    @Override protected void onSaveInstanceState(Bundle out){out.putInt("tab",selectedTab);super.onSaveInstanceState(out);}
    @Override public boolean onCreateOptionsMenu(Menu menu){getMenuInflater().inflate(R.menu.toolbar,menu);menu.findItem(R.id.action_add).setVisible(selectedTab==R.id.nav_contacts);return true;}
    @Override public boolean onOptionsItemSelected(MenuItem item){int id=item.getItemId();if(id==R.id.action_dialpad){openDialpad("");return true;}if(id==R.id.action_settings){settings();return true;}if(id==R.id.action_add){Intent i=new Intent(Intent.ACTION_INSERT,android.provider.ContactsContract.Contacts.CONTENT_URI);try{startActivity(i);}catch(Exception e){Toast.makeText(this,R.string.action_unavailable,Toast.LENGTH_SHORT).show();}return true;}return super.onOptionsItemSelected(item);}
    private void settings(){String[] entries={getString(R.string.appearance),getString(R.string.default_phone),getString(R.string.privacy),getString(R.string.about)};new AlertDialog.Builder(this).setTitle(R.string.settings).setItems(entries,(d,w)->{if(w==0)appearance();else if(w==1)requestDefaultRole();else if(w==2)new AlertDialog.Builder(this).setTitle(R.string.privacy).setMessage(R.string.privacy_body).setPositiveButton(android.R.string.ok,null).show();else new AlertDialog.Builder(this).setTitle(R.string.about).setMessage(getString(R.string.about_body,getString(R.string.app_name),getPackageManagerVersion())).setPositiveButton(android.R.string.ok,null).show();}).show();}
    private String getPackageManagerVersion(){try{return getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(Exception e){return "1.0";}}
    private void appearance(){String[] names={getString(R.string.theme_system),getString(R.string.theme_light),getString(R.string.theme_dark),getString(R.string.accent_blue),getString(R.string.accent_sky),getString(R.string.accent_indigo),getString(R.string.accent_teal),getString(R.string.density_comfortable),getString(R.string.density_compact),getString(R.string.haptics)};new AlertDialog.Builder(this).setTitle(R.string.appearance).setItems(names,(d,w)->{if(w<3){getPreferences(0).edit().putString("theme",new String[]{"system","light","dark"}[w]).apply();applyTheme();}else if(w<7){getPreferences(0).edit().putInt("accent",new int[]{0xFF3B82F6,0xFF38BDF8,0xFF6366F1,0xFF14B8A6}[w-3]).apply();applyNavigationAccent();showTab(selectedTab);}else if(w<9){getPreferences(0).edit().putBoolean("compact",w==8).apply();showTab(selectedTab);}else{boolean enabled=getPreferences(0).getBoolean("haptics",true);getPreferences(0).edit().putBoolean("haptics",!enabled).apply();Toast.makeText(this,!enabled?R.string.haptics_on:R.string.haptics_off,Toast.LENGTH_SHORT).show();}}).show();}
    private void requestDefaultRole(){if(Build.VERSION.SDK_INT>=29){RoleManager r=getSystemService(RoleManager.class);if(r!=null&&r.isRoleAvailable(RoleManager.ROLE_DIALER)&&!r.isRoleHeld(RoleManager.ROLE_DIALER))roleRequest.launch(r.createRequestRoleIntent(RoleManager.ROLE_DIALER));else Toast.makeText(this,R.string.default_status,Toast.LENGTH_LONG).show();}else{try{startActivity(new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS));}catch(Exception e){Toast.makeText(this,R.string.action_unavailable,Toast.LENGTH_SHORT).show();}}}
}
