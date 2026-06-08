package code.name.monkey.retromusic.javatube;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;

public class Cipher {

    private static String playerJs;
    private static String signatureFunctionName;
    private static int signatureParam = 0; 
    private static String nsigFunctionName;
    private final String js;

    public Cipher(String jsCode, String ytPlayerJs) throws Exception {
        this.js = jsCode;
        playerJs = ytPlayerJs;
        signatureFunctionName = getSigFunctionName(jsCode);
        nsigFunctionName = getNsigFunctionName(jsCode);
    }

    public Cipher(String jsCode) throws Exception {
        this(jsCode, "https://youtube.com/s/player/default/base.js");
    }

    private static String getSigFunctionName(String js) throws Exception {
        // CORRECCIÓN API 21: Mantenemos la estructura pero contamos los índices posicionales de los paréntesis ( )
        // Patrón 0: grupo 1 = sig, grupo 2 = arg
        // Patrón 1: grupo 1 = var, grupo 2 = sig, grupo 3 = param
        // Patrón 2: grupo 1 = sig
        String[] functionPattern = {
                "([a-zA-Z0-9_$]+)\\s*=\\s*function\\(\\s*([a-zA-Z0-9_$]+)\\s*\\)\\s*\\{\\s*(\\2)\\s*=\\s*(\\2)\\.split\\(\\s*[a-zA-Z0-9_\\$\\\"\\[\\]]+\\s*\\)\\s*;\\s*[^}]+;\\s*return\\s+(\\2)\\.join\\(\\s*[a-zA-Z0-9_\\$\\\"\\[\\]]+\\s*\\)",
                "\\b([a-zA-Z0-9_$]+)&&\\((\\1)=([a-zA-Z0-9_$]{2,})\\((?:(\\d+),decodeURIComponent)\\((\\1)\\)\\)",
                "(?:\\b|[^a-zA-Z0-9_$])([a-zA-Z0-9_$]{2,})\\s*=\\s*function\\(\\s*a\\s*\\)\\s*\\{\\s*a\\s*=\\s*a\\.split\\(\\s*\\\"\\\"\\s*\\)(?:;[a-zA-Z0-9_$]{2}\\.[a-zA-Z0-9_$]{2}\\(a,\\d+\\))?"
        };

        for (int i = 0; i < functionPattern.length; i++) {
            Pattern regex = Pattern.compile(functionPattern[i]);
            Matcher matcher = regex.matcher(js);
            if (matcher.find()) {
                signatureParam = 0; // Reset por seguridad
                
                try {
                    if (i == 0) {
                        return matcher.group(1); // Patrón 0: 'sig' es el grupo 1
                    } else if (i == 1) {
                        // Patrón 1: 'sig' es el grupo 3, 'param' es el grupo 4
                        if (matcher.groupCount() >= 4) {
                            String paramStr = matcher.group(4);
                            if (paramStr != null && !paramStr.isEmpty()) {
                                signatureParam = Integer.parseInt(paramStr);
                            }
                        }
                        return matcher.group(3); 
                    } else if (i == 2) {
                        return matcher.group(1); // Patrón 2: 'sig' es el grupo 1
                    }
                } catch (Exception e) {
                    signatureParam = 0;
                }
            }
        }
        throw new Exception("getSigFunctionName: Could not find function name in playerJs: " + playerJs);
    }

    private String getNsigFunctionName(String js) throws Exception {
        // CORRECCIÓN API 21: Eliminamos el tag (?<funcname>...) y usamos el grupo indexado 1
        String pattern = "var\\s*[a-zA-Z0-9$_]{3}\\s*=\\s*\\[([a-zA-Z0-9$_]{3})\\]";
        Pattern regex = Pattern.compile(pattern);
        Matcher matcher = regex.matcher(js);
        if (matcher.find()) {
            return matcher.group(1); // Grupo 1 directo
        }
        throw new Exception("getNsigFunctionName: Could not find function name in playerJs: " + playerJs);
    }

    public String getSignature(String cipherSignature) throws Exception {
        Context context = Context.enter();
        context.setOptimizationLevel(-1);
        try {
            Scriptable scope = context.initStandardObjects();
            context.evaluateString(scope, js, "<youtube_base_js>", 1, null);
            
            // Si capturamos un parámetro numérico (Patrón 1), se lo inyectamos a la función
            String script = (signatureParam != 0) 
                ? "var result = " + signatureFunctionName + "(" + signatureParam + ", '" + cipherSignature + "');"
                : "var result = " + signatureFunctionName + "('" + cipherSignature + "');";
                
            context.evaluateString(scope, script, "<execute_sig>", 1, null);
            return scope.get("result", scope).toString();
        } catch (Exception e) {
            // Fallback secundario si falla la ejecución por sobrecarga de argumentos
            try {
                Scriptable scope = context.initStandardObjects();
                context.evaluateString(scope, js, "<youtube_base_js>", 1, null);
                String script = "var result = " + signatureFunctionName + "('" + cipherSignature + "');";
                context.evaluateString(scope, script, "<execute_sig_fallback>", 1, null);
                return scope.get("result", scope).toString();
            } catch (Exception ex) {
                throw new Exception("Fallo en descifrado Signature: " + ex.getMessage());
            }
        } finally {
            Context.exit();
        }
    }

    public String getNSig(String n) throws Exception {
        Context context = Context.enter();
        context.setOptimizationLevel(-1);
        try {
            Scriptable scope = context.initStandardObjects();
            context.evaluateString(scope, js, "<youtube_base_js>", 1, null);
            
            String script = "var result = " + nsigFunctionName + "('" + n + "');";
            context.evaluateString(scope, script, "<execute_nsig>", 1, null);
            
            return scope.get("result", scope).toString();
        } catch (Exception e) {
            e.printStackTrace();
            return n; 
        } finally {
            Context.exit();
        }
    }

    public String calculateN(String n) throws Exception {
        return getNSig(n);
    }
}
