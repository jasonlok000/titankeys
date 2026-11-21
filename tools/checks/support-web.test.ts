import { expect, test } from "bun:test";
import { readFileSync } from "node:fs";
import { join } from "node:path";

// The support page is one offline HTML page in a WebView (owner, 2026-11-19). It loads only its own assets, has no network,
// file access or third-party content, debugs only in TitanKeys Debug, and talks through one checked bridge.
const root = join(import.meta.dir, "../..");
const read = (path: string) => readFileSync(join(root, path), "utf8");
const activity = read("android/kotlin/com/titankeys/support/SupportWebActivity.kt");

test("the WebView loads only the page's assets: everything else gets an empty 403, and the network is off", () => {
  for (const line of ["allowFileAccess = false", "allowContentAccess = false", "blockNetworkLoads = true",
    "mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW", "setSupportMultipleWindows(false)", "javaScriptCanOpenWindowsAutomatically = false"])
    expect(activity).toContain(line);
  expect(activity).toContain("val path = SupportWebAssets.assetPath(request.url.toString()) ?: return refused()");
  expect(activity).toContain('WebResourceResponse("text/plain", "utf-8", 403, "Forbidden"');
  expect(activity).toContain("connect-src 'none'");
  expect(activity).toContain('WebView.setWebContentsDebuggingEnabled(AppBuild.kind(this) == "debug")');
  expect(activity).toContain("web.loadUrl(SupportWebAssets.start(mode))");
  expect(activity).not.toMatch(/loadUrl\("(http|file)/);
});

test("every bridge message is checked before it runs, and only from the page's own origin", () => {
  // A message from before the page counts as ours waits for it instead of running (or, as before, being dropped: the page
  // then stayed blank, 2026-11-20); it runs only once the page is ours, through the same check.
  expect(activity).toContain("if (!pageReady || SupportWebAssets.assetPath(web.url.orEmpty()) == null) {");
  expect(activity).toContain("if (!pageReady || SupportWebAssets.assetPath(web.url.orEmpty()) == null) return\n        waiting.forEach(::take)");
  expect(activity).toContain("SupportWebProtocol.parse(message)");
  expect(activity).toContain("main.post { take(message.orEmpty()) }");
  expect(activity).toMatch(/@JavascriptInterface\s+fun post\(message: String\?\)/);
  expect(activity.match(/@JavascriptInterface/g)).toHaveLength(1);
  expect(activity).toContain("web.evaluateJavascript(SupportWebProtocol.deliver(message), null)");
});

test("the page opens from the card at the very bottom of Settings and from the front page; the invite is its popup", () => {
  const settings = read("app/settings/index.tsx");
  const card = settings.indexOf('<SupportCard title="Support TitanKeys"');
  expect(card).toBeGreaterThan(settings.indexOf('<Section title="Start over">'));
  expect(settings.slice(card)).toContain('onPress={() => void openSupport("page")}');
  expect(read("app/index.tsx")).toContain('else if (action === "support") void openSupport("page");');
  expect(read("android/kotlin/com/titankeys/keyboard/GuideActivity.kt"))
    .toContain('startActivity(com.titankeys.support.SupportWebActivity.intent(this, "popup"))');
  const manifest = read("android/src/main/AndroidManifest.xml");
  expect(manifest).toMatch(/android:name="com\.titankeys\.support\.SupportWebActivity"\s+android:exported="false"/);
  // The Debug preview answers through the same bridge.
  expect(activity).toContain('preview = AppBuild.kind(this) == "debug" && intent.getBooleanExtra(PREVIEW_FIXTURE, false)');
});
