package cn.ling.encode;

import org.mindrot.jbcrypt.BCrypt;

public class BCryptUtils {
    //获取加密后的结果
    public static String encode(String password) {
        return BCrypt.hashpw(password, BCrypt.gensalt());
    }

    //比较加密前跟加密后是否一致
    public static Boolean judge(String password,String encode){
        return BCrypt.checkpw(password, encode);
    }
}
