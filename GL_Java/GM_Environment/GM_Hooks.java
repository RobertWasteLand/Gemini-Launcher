package GL_Java.GM_Environment;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

import me.zed_0xff.zombie_buddy.Patch;
import zombie.core.Core;

final class GM_Hooks {
    static final String GM_Verified = "42.21.0 4a0e9546ec";
    private static final int GM_Shown_Max = 6;

    private final ClassLoader loader;
    private final Set<String> seen = new LinkedHashSet<>();
    private final List<String> missing = new ArrayList<>();
    private int patches;

    private GM_Hooks(ClassLoader loader) {
        this.loader = loader;
    }

    static String GM_Run() {
        long start = System.nanoTime();
        String version = GM_Version();
        GM_Hooks hooks = new GM_Hooks(GM_Hooks.class.getClassLoader());
        try {
            Path source = Path.of(GM_Hooks.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            hooks.GM_Scan(source);
        } catch (Throwable t) {
            System.out.println("[GL_Java] GM_Environment game check skipped (" + t + ")");
            return null;
        }
        String ms = String.format(Locale.ROOT, "%.0f", (System.nanoTime() - start) / 1.0e6);
        String game = version + (GM_Verified.equals(version) ? " (verified)" : " (not verified yet, built for " + GM_Verified + ")");
        if (hooks.missing.isEmpty()) {
            System.out.println("[GL_Java] GM_Environment game check: " + hooks.seen.size() + " game hooks and " + hooks.patches + " patches ok on " + game + ", " + ms + " ms");
            return null;
        }
        List<String> shown = hooks.missing.subList(0, Math.min(GM_Shown_Max, hooks.missing.size()));
        String more = hooks.missing.size() > shown.size() ? " and " + (hooks.missing.size() - shown.size()) + " more" : "";
        return "the game changed (" + game + "), " + hooks.missing.size() + " of " + hooks.seen.size() + " hooks are gone: " + String.join(", ", shown) + more;
    }

    private static String GM_Version() {
        try {
            String version = Core.getInstance().getVersion();
            return version == null ? "unknown" : version.trim();
        } catch (Throwable t) {
            return "unknown";
        }
    }

    private void GM_Scan(Path source) throws IOException {
        if (Files.isDirectory(source)) {
            try (Stream<Path> files = Files.walk(source)) {
                for (Path file : (Iterable<Path>) files.filter(f -> f.toString().endsWith(".class")).sorted()::iterator) {
                    GM_Class(source.relativize(file).toString().replace('\\', '/'), Files.readAllBytes(file));
                }
            }
            return;
        }
        try (JarFile jar = new JarFile(source.toFile())) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (!entry.getName().endsWith(".class")) {
                    continue;
                }
                try (InputStream in = jar.getInputStream(entry)) {
                    GM_Class(entry.getName(), in.readAllBytes());
                }
            }
        }
    }

    private void GM_Class(String path, byte[] bytes) throws IOException {
        if (!path.startsWith("GL_Java/GM_Environment/")) {
            return;
        }
        GM_Pool(bytes);
        Class<?> own;
        try {
            own = Class.forName(path.substring(0, path.length() - 6).replace('/', '.'), false, loader);
        } catch (Throwable t) {
            return;
        }
        Patch patch = own.getAnnotation(Patch.class);
        if (patch != null) {
            patches++;
            GM_Patch(own, patch);
        }
    }

    private void GM_Pool(byte[] bytes) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
        in.readInt();
        in.readUnsignedShort();
        in.readUnsignedShort();
        int count = in.readUnsignedShort();
        int[] tag = new int[count];
        int[] first = new int[count];
        int[] second = new int[count];
        String[] text = new String[count];
        for (int i = 1; i < count; i++) {
            int t = in.readUnsignedByte();
            tag[i] = t;
            switch (t) {
                case 1:
                    text[i] = in.readUTF();
                    break;
                case 3:
                case 4:
                    in.readInt();
                    break;
                case 5:
                case 6:
                    in.readLong();
                    i++;
                    break;
                case 7:
                case 8:
                case 16:
                case 19:
                case 20:
                    first[i] = in.readUnsignedShort();
                    break;
                case 9:
                case 10:
                case 11:
                case 12:
                case 17:
                case 18:
                    first[i] = in.readUnsignedShort();
                    second[i] = in.readUnsignedShort();
                    break;
                case 15:
                    in.readUnsignedByte();
                    first[i] = in.readUnsignedShort();
                    break;
                default:
                    throw new IOException("unknown constant " + t);
            }
        }
        for (int i = 1; i < count; i++) {
            if (tag[i] == 7) {
                GM_Type(text[first[i]]);
            } else if (tag[i] == 9 || tag[i] == 10 || tag[i] == 11) {
                String owner = text[first[first[i]]];
                int nat = second[i];
                GM_Member(tag[i] == 9, owner, text[first[nat]], text[second[nat]]);
            }
        }
    }

    private static boolean GM_Game(String owner) {
        return !(owner.startsWith("java/") || owner.startsWith("javax/") || owner.startsWith("jdk/") || owner.startsWith("sun/")
            || owner.startsWith("GL_Java/") || owner.startsWith("me/zed_0xff/"));
    }

    private void GM_Type(String name) {
        String owner = name.replaceFirst("^\\[+", "");
        if (owner.length() != name.length()) {
            if (!owner.startsWith("L")) {
                return;
            }
            owner = owner.substring(1, owner.length() - 1);
        }
        if (!GM_Game(owner) || !seen.add(owner)) {
            return;
        }
        Class<?> c = GM_Load(owner);
        if (c == null) {
            missing.add(owner.replace('/', '.'));
        } else if (!Modifier.isPublic(c.getModifiers())) {
            missing.add(owner.replace('/', '.') + " (no longer public)");
        }
    }

    private void GM_Member(boolean field, String owner, String name, String desc) {
        if (!GM_Game(owner) || !seen.add(owner + "." + name + desc)) {
            return;
        }
        String label = owner.replace('/', '.') + "." + name;
        Class<?> c = GM_Load(owner);
        if (c == null) {
            missing.add(label);
            return;
        }
        try {
            int modifiers = field ? GM_Field(c, name, desc) : GM_Method(c, name, desc);
            if (modifiers < 0) {
                missing.add(label);
            } else if (!Modifier.isPublic(modifiers)) {
                missing.add(label + " (no longer public)");
            }
        } catch (Throwable t) {
            missing.add(label + " (" + t.getClass().getSimpleName() + ")");
        }
    }

    private Class<?> GM_Load(String owner) {
        try {
            return Class.forName(owner.replace('/', '.'), false, loader);
        } catch (Throwable t) {
            return null;
        }
    }

    private static int GM_Field(Class<?> c, String name, String desc) {
        for (Class<?> k = c; k != null; k = k.getSuperclass()) {
            for (Field f : k.getDeclaredFields()) {
                if (f.getName().equals(name) && GM_Desc(f.getType()).equals(desc)) {
                    return f.getModifiers();
                }
            }
            for (Class<?> i : k.getInterfaces()) {
                int found = GM_Field(i, name, desc);
                if (found >= 0) {
                    return found;
                }
            }
        }
        return -1;
    }

    private static int GM_Method(Class<?> c, String name, String desc) {
        if (name.equals("<init>")) {
            for (Constructor<?> k : c.getDeclaredConstructors()) {
                if (GM_Desc(k.getParameterTypes(), void.class).equals(desc)) {
                    return k.getModifiers();
                }
            }
            return -1;
        }
        for (Class<?> k = c; k != null; k = k.getSuperclass()) {
            for (Method m : k.getDeclaredMethods()) {
                if (m.getName().equals(name) && GM_Desc(m.getParameterTypes(), m.getReturnType()).equals(desc)) {
                    return m.getModifiers();
                }
            }
            for (Class<?> i : k.getInterfaces()) {
                int found = GM_Method(i, name, desc);
                if (found >= 0) {
                    return found;
                }
            }
        }
        return -1;
    }

    private static String GM_Desc(Class<?>[] params, Class<?> result) {
        StringBuilder out = new StringBuilder("(");
        for (Class<?> p : params) {
            out.append(GM_Desc(p));
        }
        return out.append(')').append(GM_Desc(result)).toString();
    }

    private static String GM_Desc(Class<?> c) {
        if (c.isArray()) {
            return c.getName().replace('.', '/');
        }
        if (c == void.class) {
            return "V";
        }
        if (c == boolean.class) {
            return "Z";
        }
        if (c == byte.class) {
            return "B";
        }
        if (c == char.class) {
            return "C";
        }
        if (c == short.class) {
            return "S";
        }
        if (c == int.class) {
            return "I";
        }
        if (c == long.class) {
            return "J";
        }
        if (c == float.class) {
            return "F";
        }
        if (c == double.class) {
            return "D";
        }
        return "L" + c.getName().replace('.', '/') + ";";
    }

    private void GM_Patch(Class<?> own, Patch patch) {
        String label = "patch " + patch.className() + "." + patch.methodName();
        seen.add(label);
        Class<?> target;
        try {
            target = Class.forName(patch.className(), false, loader);
        } catch (Throwable t) {
            missing.add(label);
            return;
        }
        List<Method> candidates = new ArrayList<>();
        try {
            for (Method m : target.getDeclaredMethods()) {
                if (m.getName().equals(patch.methodName())) {
                    candidates.add(m);
                }
            }
            for (Method advice : own.getDeclaredMethods()) {
                if (GM_Advice(advice)) {
                    candidates.removeIf(m -> !GM_Fits(advice, m));
                }
            }
        } catch (Throwable t) {
            candidates.clear();
        }
        if (candidates.isEmpty()) {
            missing.add(label);
        }
    }

    private static boolean GM_Advice(Method advice) {
        for (Annotation a : advice.getAnnotations()) {
            String name = a.annotationType().getSimpleName();
            if (name.equals("OnEnter") || name.equals("OnExit") || name.equals("OnMethodEnter") || name.equals("OnMethodExit")) {
                return true;
            }
        }
        return false;
    }

    private static Object GM_Attribute(Annotation a, String name, Object fallback) {
        try {
            return a.annotationType().getMethod(name).invoke(a);
        } catch (Throwable t) {
            return fallback;
        }
    }

    private static boolean GM_Fits(Method advice, Method target) {
        Class<?>[] types = advice.getParameterTypes();
        Annotation[][] annotations = advice.getParameterAnnotations();
        Class<?>[] params = target.getParameterTypes();
        for (int i = 0; i < types.length; i++) {
            for (Annotation a : annotations[i]) {
                String name = a.annotationType().getSimpleName();
                if (name.equals("This")) {
                    if (Modifier.isStatic(target.getModifiers()) || !types[i].isAssignableFrom(target.getDeclaringClass())) {
                        return false;
                    }
                } else if (name.equals("Argument")) {
                    int n = (Integer) GM_Attribute(a, "value", 0);
                    if (n >= params.length || !GM_Assignable(types[i], params[n])) {
                        return false;
                    }
                } else if (name.equals("Return")) {
                    Class<?> result = target.getReturnType();
                    boolean readOnly = (Boolean) GM_Attribute(a, "readOnly", Boolean.TRUE);
                    if (readOnly ? !GM_Assignable(types[i], result) : types[i] != result) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static boolean GM_Assignable(Class<?> to, Class<?> from) {
        if (to.isPrimitive() || from.isPrimitive()) {
            return to == from;
        }
        return to.isAssignableFrom(from);
    }
}
