# Turns ics-openvpn's :main application module into an Android library (AAR) for Ionity VPN.
# GPLv2 â€” same as ics-openvpn. Run once on a fresh checkout of tag v0.7.58.
import re, sys, pathlib
root = pathlib.Path(sys.argv[1])
p = root / 'main' / 'build.gradle.kts'
s = p.read_text(encoding='utf-8')
orig = s

s = s.replace('import com.android.build.gradle.api.ApplicationVariant', 'import com.android.build.gradle.api.LibraryVariant')
s = s.replace('alias(libs.plugins.android.application)', 'id("com.android.library")')
s = s.replace('ndkVersion = "28.0.13004108"', 'ndkVersion = "27.1.12297006"')
s = re.sub(r'\n\s*targetSdk = 35\n', '\n', s)
s = re.sub(r'\n\s*versionCode = \d+\n', '\n', s)
s = re.sub(r'\n\s*versionName = "[^"]+"\n', '\n', s)
s = s.replace('            versionNameSuffix = "-o2"\n', '')
# signing configs, release signing, splits and bundle are application-only
s = re.sub(r'\n    signingConfigs \{.*?\n    \}\n', '\n', s, flags=re.S)
s = re.sub(r'\n    buildTypes \{.*?\n    \}\n', '\n', s, flags=re.S)
s = re.sub(r'\n    splits \{.*?\n    \}\n', '\n', s, flags=re.S)
s = re.sub(r'\n    bundle \{.*?\n    \}\n(?=\}\n)', '\n', s, flags=re.S)
# ABIs: phones (arm) + x86_64 emulators
s = s.replace('        externalNativeBuild {\n            cmake {\n                //arguments+= "-DCMAKE_VERBOSE_MAKEFILE=1"',
              '        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }\n        consumerProguardFiles("consumer-rules.pro")\n        externalNativeBuild {\n            cmake {\n                //arguments+= "-DCMAKE_VERBOSE_MAKEFILE=1"')
s = s.replace('android.applicationVariants.all(object : Action<ApplicationVariant> {\n    override fun execute(variant: ApplicationVariant) {',
              'android.libraryVariants.all(object : Action<LibraryVariant> {\n    override fun execute(variant: LibraryVariant) {')
# swig from PATH or SWIG env
s = s.replace('var swigcmd = "swig"', 'var swigcmd = System.getenv("SWIG") ?: "swig"')

for must in ['id("com.android.library")', 'libraryVariants', 'abiFilters']:
    assert must in s, must
for gone in ['versionCode', 'splits {', 'bundle {', 'signingConfigs {']:
    assert gone not in s, gone
p.write_text(s, encoding='utf-8')
(root / 'main' / 'consumer-rules.pro').write_text('-keep class de.blinkt.openvpn.** { *; }\n-keep class net.openvpn.ovpn3.** { *; }\n', encoding='utf-8')

# Only build :main
st = root / 'settings.gradle.kts'
t = st.read_text(encoding='utf-8')
t = t.replace('include(":tlsexternalcertprovider")', '//include(":tlsexternalcertprovider")').replace('include(":remoteExample")', '//include(":remoteExample")')
st.write_text(t, encoding='utf-8')
# keepVPNAlive: a persisted JobScheduler job needs RECEIVE_BOOT_COMPLETED, and Samsung's JobScheduler
# caches a missing permission per app, crashing the VPN process on start. Ionity VPN has no
# always-on profiles, so keep the job non-persisted and never let scheduling kill the tunnel.
ka = root / 'main' / 'src' / 'main' / 'java' / 'de' / 'blinkt' / 'openvpn' / 'core' / 'keepVPNAlive.java'
k = ka.read_text(encoding='utf-8')
if 'Ionity:' not in k:
    k = k.replace('jib.setPersisted(true);', 'jib.setPersisted(false); // Ionity: not persisted')
    k = k.replace('jobScheduler.schedule(jib.build());', 'try { jobScheduler.schedule(jib.build()); } catch (RuntimeException e) { VpnStatus.logDebug("Ionity: keep-alive job not scheduled: " + e.getMessage()); }')
    assert 'Ionity: not persisted' in k and 'keep-alive job not scheduled' in k
    ka.write_text(k, encoding='utf-8')
print('patched')
