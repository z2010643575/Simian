package com.log.yh;

import android.app.Application;
import android.content.Context;
import android.util.Base64;
import android.widget.Toast;

import com.log.yh.Utils.Init;
import com.log.yh.Utils.Sp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class Hook implements IXposedHookLoadPackage {
    public Init init;
    public List<String> Answers;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        Sp.XSp_init();
        XposedHelpers.findAndHookMethod(Application.class, "attach", Context.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                super.afterHookedMethod(param);
                if (lpparam.packageName.equals("com.log.yh")) {
                    Hook_ModuleActive(lpparam.classLoader);
                    return;
                }
                Context context = (Context) param.args[0];
                if (init == null) {
                    init = new Init();
                }
                ClassLoader classLoader = context.getClassLoader();
                Toast.makeText(context, "hook成功", Toast.LENGTH_SHORT).show();
                init.setClassloader(classLoader);
                init();
            }
        });
    }

    public void init() throws Throwable {
        switch (Sp.Hook_getInt("mode")) {
            case 0:
                Encrypt_Hook();
                break;
            case 1:
                Encrypt_Hook_one();
                break;
            default:
                Encrypt_Hook();
                break;
        }
        Hook_practice();
        Hook_vip();
    }

    public void Encrypt_Hook() throws Throwable {
        ClassLoader loader = init.getClassloader();
        final Class<?> EncryptResult = XposedHelpers.findClass("com.fenbi.android.leo.webapp.secure.commands.EncryptResult", loader);
        XposedHelpers.findAndHookConstructor(EncryptResult, String.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                try {
                    String answer_str = Sp.Hook_getString("answers");
                    String decode = new String(Base64.decode(((String) param.args[0]).getBytes(), 0));
                    JSONObject json = new JSONObject(decode);
                    JSONObject otherUser = json.getJSONObject("otherUser");
                    int id = otherUser.getInt("userId");
                    String username = otherUser.getString("userName");
                    String avatarUrl = otherUser.getString("avatarUrl");
                    XposedBridge.log("当前对手id:" + id + "名称:" + username + "头像:" + avatarUrl);
                    JSONObject examVO = json.getJSONObject("examVO");
                    JSONArray questions = examVO.getJSONArray("questions");
                    for (int i = 0; i < questions.length(); i++) {
                        JSONObject question = questions.getJSONObject(i);
                        JSONArray answers = question.getJSONArray("answers");
                        answers.put(0, answer_str);
                        question.put("answers", answers);
                    }
                    examVO.put("questions", questions);
                    json.put("examVO", examVO);
                    String str = json.toString();
                    String encode = new String(Base64.encode(str.getBytes(), 0));
                    param.args[0] = encode;
                } catch (Exception e) {
                }
            }
        });
        final Class<?> JsBridgeBean = XposedHelpers.findClass("com.yuanfudao.android.common.webview.base.JsBridgeBean$a", loader);
        XposedHelpers.findAndHookConstructor(JsBridgeBean, "com.yuanfudao.android.common.webview.base.a", String.class, String.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                String answer_str = Sp.Hook_getString("answers");
                String result = (String) param.args[1];
                if (result.contains("recognize")) {
                    String num = "[null, \"" + answer_str + "\"]";
                    String encode = new String(Base64.encode(num.getBytes(), 0));
                    param.args[2] = encode;
                }
            }
        });
    }

    public void Encrypt_Hook_one() throws Throwable {
        ClassLoader loader = init.getClassloader();
        final Class<?> EncryptResult = XposedHelpers.findClass("com.fenbi.android.leo.webapp.secure.commands.EncryptResult", loader);
        XposedHelpers.findAndHookConstructor(EncryptResult, String.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                try {
                    String answer_str = Sp.Hook_getString("answers");
                    String title_str = Sp.Hook_getString("title");
                    String decode = new String(Base64.decode(((String) param.args[0]).getBytes(), 0));
                    JSONObject json = new JSONObject(decode);
                    JSONObject otherUser = json.getJSONObject("otherUser");
                    int id = otherUser.getInt("userId");
                    String username = otherUser.getString("userName");
                    String avatarUrl = otherUser.getString("avatarUrl");
                    XposedBridge.log("当前对手id:" + id + "名称:" + username + "头像:" + avatarUrl);
                    JSONObject examVO = json.getJSONObject("examVO");
                    JSONArray questions = examVO.getJSONArray("questions");
                    JSONObject last = questions.getJSONObject(questions.length() - 1);
                    last.put("title", title_str);
                    JSONArray answers = last.getJSONArray("answers");
                    answers.put(0, answer_str);
                    last.put("answers", answers);
                    JSONArray singleQuestionArray = new JSONArray();
                    singleQuestionArray.put(last);
                    examVO.put("questions", singleQuestionArray);
                    json.put("examVO", examVO);
                    String str = json.toString();
                    String encode = new String(Base64.encode(str.getBytes(), 0));
                    param.args[0] = encode;
                } catch (Exception e) {
                }
            }
        });
        final Class<?> JsBridgeBean = XposedHelpers.findClass("com.yuanfudao.android.common.webview.base.JsBridgeBean$a", loader);
        XposedHelpers.findAndHookConstructor(JsBridgeBean, "com.yuanfudao.android.common.webview.base.a", String.class, String.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                String answer_str = Sp.Hook_getString("answers");
                String result = (String) param.args[1];
                if (result.contains("recognize")) {
                    String num = "[null, \"" + answer_str + "\"]";
                    String encode = new String(Base64.encode(num.getBytes(), 0));
                    param.args[2] = encode;
                }
            }
        });
    }
    private void Hook_practice() throws Throwable {
        ClassLoader classLoader=init.getClassloader();
        Class<?> claz=XposedHelpers.findClass("com.fenbi.android.leo.exercise.data.QuestionVO",classLoader);
        XposedHelpers.findAndHookMethod(claz, "getAnswers", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                String answer=Sp.Hook_getString("practice_answer");
                Answers =(List<String>) param.getResult();
                List<String> new_Answers=new ArrayList<>();
                for (int i = 0; i< Answers.size(); i++){
                 new_Answers.add(answer);
                }
                param.setResult(new_Answers);
            }
        });
    }
    public void Hook_vip()throws Throwable{
        if (Sp.Hook_getBoolean("vip")) {
            ClassLoader classLoader = init.getClassloader();
            Class<?> UserVipVO = XposedHelpers.findClass("com.fenbi.android.leo.business.user.vip.UserVipVO", classLoader);
            XposedHelpers.findAndHookMethod(UserVipVO, "getVipSymbol", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    param.setResult(true);
                }
            });
        }
    }
    private void Hook_ModuleActive(ClassLoader classLoader) {
        XposedHelpers.findAndHookMethod("com.log.yh.MainActivity", classLoader, "isModuleActive", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                param.setResult(true);
            }
        });
    }
}
