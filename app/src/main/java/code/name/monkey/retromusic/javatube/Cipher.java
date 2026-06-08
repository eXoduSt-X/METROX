package code.name.monkey.retromusic.javatube;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Usamos Rhino nativo para Android, eliminando NodeRunner
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;

public class Cipher {

    private static String playerJs;
    private static String signatureFunctionName;
    private static int signatureParam = 0; // Guardará el entero dinámico requerido hoy
    private static String nsigFunctionName;
    private final String js;

    // Constructor compatible con el nuevo Youtube.java (recibe ambos parámetros)
    public Cipher(String jsCode, String ytPlayerJs) throws Exception {
        this.js = jsCode;
        playerJs = ytPlayerJs;
        signatureFunctionName = getSigFunctionName(jsCode);
        nsigFunctionName = getNsigFunctionName(jsCode);
    }

    // Constructor alternativo por si tu código viejo aún lo invoca con un solo argumento
    public Cipher(String jsCode) throws Exception {
        this(jsCode, "https://youtube.com/s/player/default/base.js");
    }

    private static String getSigFunctionName(String js) throws Exception {
        String[] functionPattern = {
                "(?<sig>[a-zA-Z0-9_$]+)\\s*=\\s*function\\(\\s*(?<arg>[a-zA-Z0-9_$]+)\\s*\\)\\s*\\{\\s*(\\k<arg>)\\s*=\\s*(\\k<arg>)\\.split\\(\\s*[a-zA-Z0-9_\\$\\\"\\[\\]]+\\s*\\)\\s*;\\s*[^}]+;\\s*return\\s+(\\k<arg>)\\.join\\(\\s*[a-zA-Z0-9_\\$\\\"\\[\\]]+\\s*\\)",
                "\\b(?<var>[a-zA-Z0-9_$]+)&&\\((\\k<var>)=(?<sig>[a-zA-Z0-9_$]{2,})\\((?:(?<param>\\d+),decodeURIComponent)\\((\\k<var>)\\)\\)",
                "(?:\\b|[^a-zA-Z0-9_$])(?<sig>[a-zA-Z0-9_$]{2,})\\s*=\\s*function\\(\\s*a\\s*\\)\\s*\\{\\s*a\\s*=\\s*a\\.split\\(\\s*\\\"\\\"\\s*\\)(?:;[a-zA-Z0-9_$]{2}\\.[a-zA-Z0-9_$]{2}\\(a,\\d+\\))?"
        };
        for(String pattern : functionPattern){
            Pattern regex = Pattern.compile(pattern);
            Matcher matcher = regex.matcher(js);
            if (matcher.find()) {
                // Captura el parámetro numérico si la Regex moderna lo encuentra
                try {
                    signatureParam = Integer.parseInt(matcher.group("param"));
                } catch (Exception e) {
                    signatureParam = 0; // Fallback si usa un patrón clásico
                }
                return matcher.group("sig");
            }
        }
        throw new Exception("getSigFunctionName: Could not find function name in playerJs: " + playerJs);
    }

    private String getNsigFunctionName(String js) throws Exception {
        String pattern = "var\\s*[a-zA-Z0-9$_]{3}\\s*=\\s*\\[(?<funcname>[a-zA-Z0-9$_]{3})\\]";
        Pattern regex = Pattern.compile(pattern);
        Matcher matcher = regex.matcher(js);
        if (matcher.find()) {
            return matcher.group("funcname");
        }
        throw new Exception("getNsigFunctionName: Could not find function name in playerJs: " + playerJs);
    }

    // ==========================================
    // EJECUCIÓN MATEMÁTICA CON RHINO (Bypass Node.js)
    // ==========================================

    public String getSignature(String cipherSignature) throws Exception {
        Context context = Context.enter();
        context.setOptimizationLevel(-1); // Crucial para evitar fugas de memoria en Android
        try {
            Scriptable scope = context.initStandardObjects();
            
            // Cargamos el base.js completo en el entorno aislado de Rhino
            context.evaluateString(scope, js, "<youtube_base_js>", 1, null);
            
            // Invocamos la función pasando tanto el entero de control como el token string
            String script = "var result = " + signatureFunctionName + "(" + signatureParam + ", '" + cipherSignature + "');";
            context.evaluateString(scope, script, "<execute_sig>", 1, null);
            
            Object result = scope.get("result", scope);
            return result.toString();
        } catch (Exception e) {
            // Si la función de YouTube requiere un solo parámetro en lugar de dos (fallback estructural)
            try {
                Scriptable scope = context.initStandardObjects();
                context.evaluateString(scope, js, "<youtube_base_js>", 1, null);
                String script = "var result = " + signatureFunctionName + "('" + cipherSignature + "');";
                context.evaluateString(scope, script, "<execute_sig_fallback>", 1, null);
                return scope.get("result", scope).toString();
            } catch (Exception ex) {
                throw new Exception("Fallo catastrófico en descifrado Signature: " + ex.getMessage());
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
            
            Object result = scope.get("result", scope);
            return result.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return n; // Fallback seguro para evitar que la app crasheé en caliente
        } finally {
            Context.exit();
        }
    }

    // Mantenemos el puente con tu Youtube.java viejo por si acaso
    public String calculateN(String n) throws Exception {
        return getNSig(n);
    }
}
