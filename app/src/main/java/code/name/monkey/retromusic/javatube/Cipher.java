package code.name.monkey.retromusic.javatube;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONObject;

// CORRECCIÓN: Quitamos javax.script e importamos el motor compatible con Android (Rhino)
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;

public class Cipher {

    private final String js;
    private String transformPlan = null;
    private String transformObj = null;
    private String throttlingPlan = null;
    private String throttlingFunctionName = null;
    private String throttlingRawCode = null;

    public Cipher(String jsCode) {
        js = jsCode;
    }

    private String getSignatureCode() throws Exception {
        Pattern pattern = Pattern.compile("function\\(\\w+\\)\\{\\w+=\\w+\\.split\\(\"\"\\);(.*)\\.join\\(\"\"\\)\\}");
        Matcher matcher = pattern.matcher(js);
        if (matcher.find()) {
            return matcher.group(1);
        } else {
            throw new Exception("RegexMatcherError (SignatureCode)");
        }
    }

    private String getThrottlingPlan() throws Exception {
        if (throttlingPlan == null) {
            Pattern pattern = Pattern.compile("String\\.prototype\\.split\\.call\\(\\w+,(\"\"|'')\\);(.*);return \\w+\\.join\\((\"\"|'')\\)");
            Matcher matcher = pattern.matcher(js);
            if (matcher.find()) {
                throttlingPlan = matcher.group(2);
                return throttlingPlan;
            } else {
                throw new Exception("RegexMatcherError (ThrottlingPlan)");
            }
        }
        return throttlingPlan;
    }

    public String getThrottlingFunctionName() throws Exception {
        if (throttlingFunctionName == null) {
            Pattern pattern = Pattern.compile("(\\w+)=String\\.prototype\\.split\\.call\\(\\w+,(\"\"|'')\\)");
            Matcher matcher = pattern.matcher(getThrottlingPlan());
            if (matcher.find()) {
                throttlingFunctionName = matcher.group(1);
                return throttlingFunctionName;
            } else {
                throw new Exception("RegexMatcherError (ThrottlingFunctionName)");
            }
        }
        return throttlingFunctionName;
    }

    public String getThrottlingRawCode() throws Exception {
        if (throttlingRawCode == null) {
            String functionName = getThrottlingFunctionName();
            Pattern pattern = Pattern.compile("(?s)(" + functionName + "=function\\(\\w+\\)\\{.*?\\};)");
            Matcher matcher = pattern.matcher(js);
            if (matcher.find()) {
                throttlingRawCode = matcher.group(1);
                return throttlingRawCode;
            } else {
                throw new Exception("RegexMatcherError (ThrottlingRawCode)");
            }
        }
        return throttlingRawCode;
    }

    // CORRECCIÓN: Ejecución del descifrado del parámetro N usando Rhino nativo en Android
    public String calculateN(String n) throws Exception {
        String functionName = getThrottlingFunctionName();
        String rawCode = getThrottlingRawCode();

        // Inicializamos el contexto de Rhino de forma segura para Android
        Context context = Context.enter();
        context.setOptimizationLevel(-1); // Desactiva generación de bytecode en tiempo de ejecución para evitar fallos de memoria en Android
        try {
            Scriptable scope = context.initStandardObjects();
            
            // Evaluamos la función de descifrado extraída de YouTube
            context.evaluateString(scope, rawCode, "<cmd>", 1, null);
            
            // Ejecutamos la función pasando el parámetro 'n' empaquetado
            context.evaluateString(scope, "var b = " + functionName + "('" + n + "')", "<cmd>", 1, null);
            
            // Extraemos el resultado limpio de la variable de retorno 'b'
            Object result = scope.get("b", scope);
            return result.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return n; // Fallback seguro por si cambia el algoritmo dinámico
        } finally {
            Context.exit(); // Obligatorio cerrar el contexto para evitar Memory Leaks
        }
    }

    private String getTransformPlan() throws Exception {
        if (transformPlan == null) {
            transformPlan = getSignatureCode().split(";")[0];
        }
        return transformPlan;
    }

    public String getTransformObj() throws Exception {
        if (transformObj == null) {
            String plan = getTransformPlan();
            transformObj = Arrays.asList(plan.split("\\.")).get(0);
        }
        return transformObj;
    }

    public String getInitCode() throws Exception {
        String obj = getTransformObj();
        Pattern pattern = Pattern.compile("(?s)(var " + obj + "=\\{.*?\\};)");
        Matcher matcher = pattern.matcher(js);
        if (matcher.find()) {
            return matcher.group(1);
        } else {
            throw new Exception("RegexMatcherError (InitCode)");
        }
    }

    public String getFullSignatureCode() throws Exception {
        return getInitCode() + "function descramble(a){a=a.split(\"\");" + getSignatureCode() + ";return a.join(\"\");}";
    }

    public String getSignature(String cipher) throws Exception {
        Context context = Context.enter();
        context.setOptimizationLevel(-1);
        try {
            Scriptable scope = context.initStandardObjects();
            context.evaluateString(scope, getFullSignatureCode(), "<cmd>", 1, null);
            context.evaluateString(scope, "var sig = descramble('" + cipher + "')", "<cmd>", 1, null);
            Object result = scope.get("sig", scope);
            return result.toString();
        } finally {
            Context.exit();
        }
    }
}
