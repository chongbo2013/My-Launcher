
package lewa.os;

import java.nio.charset.Charset;

import java.io.DataOutputStream;
import java.io.InputStreamReader;

import libcore.io.IoUtils;
import libcore.io.Streams;

import java.lang.Process;

public class SuExecuter {
    private static final String CONFIG_SU_BINARY = "su0";
    private static final Charset UTF_8_CODE = Charset.forName("UTF-8");
    private static final String TAG ="SuExecuter";
    private static final boolean DEBUG = false ;

    public static class CommandResult {
        public String result = "";
        public String error = "";
        public boolean success = false;

        @Override
        public String toString() {
            return "CommandResult [result=" + result + ", error=" + error + ", success=" + success
                    + "]";
        }
    }

    public static CommandResult runCommandForResult(String command, boolean root) {
        Process process = null;
        DataOutputStream os = null;
        CommandResult ret = new CommandResult();
        if(DEBUG)
            android.util.Log.e(TAG, "running " + command);
        try {
            if (root) {
                process = Runtime.getRuntime().exec(CONFIG_SU_BINARY);
                os = new DataOutputStream(process.getOutputStream());
                os.writeBytes(command + "\n");
                os.writeBytes("exit\n");
                os.flush();
            }
            else {
                process = Runtime.getRuntime().exec(command);
            }
            ret.result = Streams.readFully(new InputStreamReader(process.getInputStream(),
                    UTF_8_CODE));
            ret.error = Streams.readFully(new InputStreamReader(process.getErrorStream(),
                    UTF_8_CODE));
            ret.success = process.waitFor() == 0;
        }
        catch (Exception e) {
            ret.result = "";
            ret.error = e.getMessage();
        }
        finally {
            IoUtils.closeQuietly(os);
        }
        return ret;
    }
}
