package com.example.pulsedialer;
import java.util.Locale;
import java.text.Normalizer;
public final class T9 {
 private T9() {}
 public static String digitsFor(String text) {
  StringBuilder out=new StringBuilder(); if(text==null)return "";String normalized=Normalizer.normalize(text,Normalizer.Form.NFD).replaceAll("\\p{M}+","");
  for(char c:normalized.toLowerCase(Locale.US).toCharArray()){if(c>='a'&&c<='c')out.append('2');else if(c>='d'&&c<='f')out.append('3');else if(c>='g'&&c<='i')out.append('4');else if(c>='j'&&c<='l')out.append('5');else if(c>='m'&&c<='o')out.append('6');else if(c>='p'&&c<='s')out.append('7');else if(c>='t'&&c<='v')out.append('8');else if(c>='w'&&c<='z')out.append('9');else if(c>='0'&&c<='9')out.append(c);}
  return out.toString();
 }
 public static boolean matches(String query,String name,String number){String q=digitsFor(query);return q.isEmpty()||digitsFor(name).contains(q)||(number!=null&&number.replaceAll("\\D","").contains(q));}
}
