package com.example.pulsedialer;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DialpadFragment extends Fragment {
    private static final ExecutorService WORKER=Executors.newSingleThreadExecutor();
    private EditText number;private RecyclerView suggestions;private SuggestionAdapter adapter;private List<ContactRepository.Contact> all=new ArrayList<>();private List<PhoneAccountHandle> accounts=new ArrayList<>();private int accent;private View rootView;private LinearLayout accountRow;private Button accountAccess;
    private PhoneAccountHandle pendingAccount;
    private final ActivityResultLauncher<String> callPermission=registerForActivityResult(new ActivityResultContracts.RequestPermission(),granted->{if(granted)placeSelected(pendingAccount);else startDialFallback();});
    static DialpadFragment newInstance(String value){DialpadFragment f=new DialpadFragment();Bundle b=new Bundle();b.putString("number",value);f.setArguments(b);return f;}
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater i,@Nullable ViewGroup p,Bundle b){return i.inflate(R.layout.fragment_dialpad,p,false);}
    @Override public void onViewCreated(@NonNull View v,Bundle b){rootView=v;number=v.findViewById(R.id.number);number.setShowSoftInputOnFocus(false);number.setText(getArguments()==null?"":getArguments().getString("number",""));accent=requireActivity().getPreferences(0).getInt("accent",0xFF3B82F6);accountRow=v.findViewById(R.id.sim_actions);accountAccess=v.findViewById(R.id.account_access);GridLayout grid=v.findViewById(R.id.keypad);String[][] keys={{"1",""},{"2","ABC"},{"3","DEF"},{"4","GHI"},{"5","JKL"},{"6","MNO"},{"7","PQRS"},{"8","TUV"},{"9","WXYZ"},{"*",""},{"0","+"},{"#",""}};for(String[] key:keys){Button x=new Button(requireContext());x.setText(key[0]+(key[1].isEmpty()?"":"\n"+key[1]));x.setTextSize(18);x.setMinHeight(dp(58));x.setBackgroundTintList(ColorStateList.valueOf(accent));x.setContentDescription(getString(R.string.digit_description,key[0],key[1]));x.setOnClickListener(z->{if(requireActivity().getPreferences(0).getBoolean("haptics",true))x.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);int at=number.getSelectionStart();if(at<0)at=number.length();number.getText().insert(at,key[0]);});x.setOnLongClickListener(z->{if("0".equals(key[0])){int at=number.getSelectionStart();if(at<0)at=number.length();number.getText().insert(at,"+");return true;}return false;});GridLayout.LayoutParams lp=new GridLayout.LayoutParams();lp.width=0;lp.height=0;lp.columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f);lp.rowSpec=GridLayout.spec(GridLayout.UNDEFINED,1f);lp.setMargins(dp(5),dp(3),dp(5),dp(3));grid.addView(x,lp);}v.findViewById(R.id.call).setBackgroundTintList(ColorStateList.valueOf(accent));
        Button back=v.findViewById(R.id.backspace);back.setOnClickListener(x->{int end=number.getSelectionEnd();if(end>0)number.getText().delete(end-1,end);});back.setOnLongClickListener(x->{number.setText("");return true;});
        suggestions=v.findViewById(R.id.suggestions);suggestions.setLayoutManager(new LinearLayoutManager(requireContext()));adapter=new SuggestionAdapter();suggestions.setAdapter(adapter);
        number.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int before,int count){findSuggestions(s.toString());}public void afterTextChanged(Editable e){}});
        v.findViewById(R.id.call).setOnClickListener(x->placeSelected(null));
        v.findViewById(R.id.contact_access).setOnClickListener(x->{if(!MainActivity.hasPermission(requireContext(),Manifest.permission.READ_CONTACTS))((MainActivity)requireActivity()).requestContactAccess();else loadContacts();});
        accountAccess.setOnClickListener(x->((MainActivity)requireActivity()).requestPhoneStateAccess());
        if(MainActivity.hasPermission(requireContext(),Manifest.permission.READ_CONTACTS))loadContacts();refreshAccounts();setupAccountButtons(v,accent);
    }
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private void loadContacts(){WORKER.execute(()->{List<ContactRepository.Contact> result=ContactRepository.contacts(requireContext().getApplicationContext());if(getActivity()!=null)getActivity().runOnUiThread(()->{all=result;findSuggestions(number.getText().toString());});});}
    private void findSuggestions(String query){if(adapter==null)return;adapter.rows.clear();String q=query.trim();if(!q.isEmpty())for(ContactRepository.Contact c:all)if(T9.matches(q,c.name,c.number)){adapter.rows.add(c);if(adapter.rows.size()==6)break;}adapter.notifyDataSetChanged();}
    private void refreshAccounts(){accounts=new ArrayList<>();if(ContextCompat.checkSelfPermission(requireContext(),Manifest.permission.READ_PHONE_STATE)!=PackageManager.PERMISSION_GRANTED)return;try{TelecomManager tm=(TelecomManager)requireContext().getSystemService(Context.TELECOM_SERVICE);accounts=tm==null?new ArrayList<>():tm.getCallCapablePhoneAccounts();}catch(SecurityException ignored){}}
    void onPermissionChanged(){if(MainActivity.hasPermission(requireContext(),Manifest.permission.READ_CONTACTS))loadContacts();refreshAccounts();setupAccountButtons(rootView,accent);}
    private void setupAccountButtons(View root,int accent){accountRow.removeAllViews();if(ContextCompat.checkSelfPermission(requireContext(),Manifest.permission.READ_PHONE_STATE)!=PackageManager.PERMISSION_GRANTED){accountRow.setVisibility(View.GONE);accountAccess.setVisibility(View.VISIBLE);return;}accountAccess.setVisibility(View.GONE);if(accounts.size()<2){accountRow.setVisibility(View.GONE);return;}accountRow.setVisibility(View.VISIBLE);TelecomManager tm=(TelecomManager)requireContext().getSystemService(Context.TELECOM_SERVICE);for(int i=0;i<accounts.size();i++){PhoneAccountHandle handle=accounts.get(i);android.telecom.PhoneAccount account=tm==null?null:tm.getPhoneAccount(handle);String label=account==null?getString(R.string.call_account_label,i+1):account.getLabel().toString();com.google.android.material.button.MaterialButton button=new com.google.android.material.button.MaterialButton(requireContext());button.setText(label);button.setAllCaps(false);button.setContentDescription(getString(R.string.call_using_account,label));button.setBackgroundTintList(ColorStateList.valueOf(accent));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(52),1f);lp.setMargins(dp(4),dp(4),dp(4),dp(4));accountRow.addView(button,lp);button.setOnClickListener(v->placeSelected(handle));}}
    private void placeSelected(PhoneAccountHandle account){String raw=number.getText().toString().trim();if(raw.isEmpty()){Toast.makeText(requireContext(),R.string.enter_number,Toast.LENGTH_SHORT).show();return;}String clean=raw.replaceAll("[^0-9+*#;,N]","");Uri uri=Uri.fromParts("tel",clean,null);if(!MainActivity.isDefaultDialer(requireContext())){try{startActivity(new Intent(Intent.ACTION_DIAL,uri));}catch(Exception e){Toast.makeText(requireContext(),R.string.call_failed,Toast.LENGTH_LONG).show();}return;}
        if(accounts.size()>1&&account==null){String[] labels=new String[accounts.size()];TelecomManager tm=(TelecomManager)requireContext().getSystemService(Context.TELECOM_SERVICE);for(int i=0;i<labels.length;i++){android.telecom.PhoneAccount a=tm==null?null:tm.getPhoneAccount(accounts.get(i));labels[i]=a==null?getString(R.string.call_account_label,i+1):a.getLabel().toString();}new AlertDialog.Builder(requireContext()).setTitle(R.string.choose_sim).setItems(labels,(d,w)->placeSelected(accounts.get(w))).setNegativeButton(android.R.string.cancel,null).show();return;}
        if(ContextCompat.checkSelfPermission(requireContext(),Manifest.permission.CALL_PHONE)!=PackageManager.PERMISSION_GRANTED){pendingAccount=account;callPermission.launch(Manifest.permission.CALL_PHONE);return;}
        try{TelecomManager tm=(TelecomManager)requireContext().getSystemService(Context.TELECOM_SERVICE);android.os.Bundle extras=new android.os.Bundle();if(account!=null)extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE,account);tm.placeCall(uri,extras);}catch(SecurityException e){startDialFallback();}catch(Exception e){Toast.makeText(requireContext(),R.string.call_failed,Toast.LENGTH_LONG).show();}}
    private void startDialFallback(){try{startActivity(new Intent(Intent.ACTION_DIAL,Uri.fromParts("tel",number.getText().toString().trim(),null)));}catch(Exception e){Toast.makeText(requireContext(),R.string.call_failed,Toast.LENGTH_LONG).show();}}
    private class SuggestionAdapter extends RecyclerView.Adapter<SuggestionHolder>{final List<ContactRepository.Contact> rows=new ArrayList<>();@NonNull public SuggestionHolder onCreateViewHolder(@NonNull ViewGroup p,int t){LinearLayout box=new LinearLayout(p.getContext());box.setGravity(Gravity.CENTER_VERTICAL);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(14),dp(8),dp(14),dp(8));TextView n=new TextView(p.getContext());n.setTextSize(15);n.setTextColor(getResources().getColor(com.google.android.material.R.color.material_dynamic_neutral10,null));TextView d=new TextView(p.getContext());d.setTextSize(12);box.addView(n);box.addView(d);return new SuggestionHolder(box,n,d);}public void onBindViewHolder(@NonNull SuggestionHolder h,int i){ContactRepository.Contact c=rows.get(i);h.name.setText(c.name);h.detail.setText(c.number);h.itemView.setOnClickListener(v->{if(c.number!=null)number.setText(c.number);});}public int getItemCount(){return rows.size();}}
    private static class SuggestionHolder extends RecyclerView.ViewHolder{TextView name,detail;SuggestionHolder(View v,TextView n,TextView d){super(v);name=n;detail=d;}}
}
