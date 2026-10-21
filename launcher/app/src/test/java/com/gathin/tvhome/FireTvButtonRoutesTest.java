package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Log lines captured from a TCL 58S571F (Fire OS 8) remote: Music, Prime Video and TCL TV buttons. */
public class FireTvButtonRoutesTest {
    private static final String MUSIC = "I/ActivityTaskManager(938): START u0 {act=android.intent.action.VIEW dat=amzns://apps/android?p=com.amazon.bueller.music cmp=com.amazon.venezia/.pdi.AppLaunchActivity} from uid 1000";
    private static final String PRIME_STORE = MUSIC.replace("p=com.amazon.bueller.music", "p=com.amazon.firebat&libraryItemId=deeplink_prime_video&libraryItemType=SHORTCUT");
    private static final String TCL = MUSIC.replace("p=com.amazon.bueller.music", "asin=B0CQF8SRMM");
    private static final String PRIME_NATIVE = "I/ActivityTaskManager(938): START u0 {act=android.intent.action.MAIN cat=[android.intent.category.LEANBACK_LAUNCHER] pkg=com.amazon.firebat cmp=com.amazon.firebat/com.amazon.firebatcore.deeplink.DeepLinkRoutingActivity} from uid 1000";
    private static final String HOME = "I/ActivityTaskManager(938): START u0 {act=android.intent.action.MAIN cat=[android.intent.category.HOME] flg=0x10000100 cmp=com.amazon.tv.launcher/.ui.HomeActivity_vNext (has extras)} from uid 0";

    private final FireTvButtonRoutes routes = new FireTvButtonRoutes(new String[]{
            "amzns://apps/android?p=com.amazon.bueller.music|com.spotify.tv.android",
            "amzns://apps/android?p=com.amazon.firebat&libraryItemId=deeplink_prime_video&libraryItemType=SHORTCUT|com.stremio.one",
            "com.amazon.firebat/com.amazon.firebatcore.deeplink.DeepLinkRoutingActivity|com.stremio.one",
            "amzns://apps/android?asin=B0CQF8SRMM|org.smarttube.stable",
    });

    @Test public void routesTheConfiguredButtons() {
        assertEquals("com.spotify.tv.android", routes.match(MUSIC));
        assertEquals("com.spotify.tv.android", routes.match(MUSIC.replace("uid 1000", "uid1000")));
        assertEquals("com.stremio.one", routes.match(PRIME_STORE));
        assertEquals("com.stremio.one", routes.match(PRIME_NATIVE));
        assertEquals("org.smarttube.stable", routes.match(TCL));
    }

    @Test public void ignoresLookalikes() {
        for (String line : new String[]{MUSIC, PRIME_STORE, TCL}) {
            assertNull(routes.match(line.replace("uid 1000", "uid 2000")));
            assertNull(routes.match(line.replace("uid 1000", "uid 10001")));
            assertNull(routes.match(line.replace("cmp=com.amazon.venezia", "cmp=other.store")));
            assertNull(routes.match(line.replace(" dat=", " dat=prefix")));
            assertNull(routes.match(line.replace(" cmp=", "&suffix=unexpected cmp=")));
            assertNull(routes.match(line.replace("START u0", "START u10")));
            assertNull(routes.match(line.replace("START u0", "Displayed")));
            assertNull(routes.match(line.replace("action.VIEW", "action.VIEW_EXTRA")));
        }
        assertNull(routes.match(MUSIC.replace("bueller.music", "bueller.music.extra")));
        assertNull(routes.match(MUSIC.replace("AppLaunchActivity", "details.AppDetailsActivity")));
        assertNull(routes.match(TCL.replace("B0CQF8SRMM", "B0CQF8SRMN")));
        assertNull(routes.match(PRIME_STORE.replace("&libraryItemId=deeplink_prime_video&libraryItemType=SHORTCUT", "")));
        assertNull(routes.match(PRIME_NATIVE.replace("uid 1000", "uid 2000")));
        assertNull(routes.match(PRIME_NATIVE.replace("category.LEANBACK_LAUNCHER", "category.LAUNCHER")));
        assertNull(routes.match(PRIME_NATIVE.replace("DeepLinkRoutingActivity", "DeepLinkRoutingActivityExtra")));
        assertNull(routes.match(HOME));
        assertNull(routes.match(null));
    }

    @Test public void skipsMalformedEntriesAndStaysOffWhenEmpty() {
        assertTrue(new FireTvButtonRoutes(new String[0]).isEmpty());
        assertTrue(new FireTvButtonRoutes(new String[]{"", "|pkg", "source|", "no-bar"}).isEmpty());
        assertNull(new FireTvButtonRoutes(new String[0]).match(MUSIC));
    }
}
