package com.openpostmobajl;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Shared parcel list helpers: row text, sorting, and delivered filtering.
 */
final class ParcelUi {

    private ParcelUi() {
    }

    static boolean isDelivered(JSONObject p) {
        String g = p.optString("statusGroup", "").toUpperCase(Locale.US);
        String s = p.optString("status", "").toUpperCase(Locale.US);
        return g.equals("DELIVERED") || s.equals("DELIVERED");
    }

    static String last6(JSONObject p) {
        String n = p.optString("shipmentNumber", p.optString("primaryParcelNumber", ""));
        return n.length() > 6 ? n.substring(n.length() - 6) : n;
    }

    static String pointName(JSONObject p) {
        JSONObject o = p.optJSONObject("pickUpPoint");
        if (o == null) {
            o = p.optJSONObject("pickUp");
        }
        return o == null ? "" : o.optString("name", "");
    }

    static String senderName(JSONObject p) {
        JSONObject o = p.optJSONObject("sender");
        return o == null ? "" : o.optString("name", "");
    }

    static String rowText(JSONObject p) {
        return last6(p)
                + "  \u00b7  " + p.optString("statusGroup", p.optString("status", ""))
                + "  \u00b7  " + pointName(p)
                + "  \u00b7  " + senderName(p)
                + "  \u203a";
    }

    /**
     * @param deliveredOnly true = only delivered parcels, false = only
     *                      non-delivered parcels.
     */
    static List<JSONObject> sorted(JSONArray parcels, boolean deliveredOnly) {
        List<JSONObject> items = new ArrayList<JSONObject>();
        for (int i = 0; i < parcels.length(); i++) {
            JSONObject p = parcels.optJSONObject(i);
            if (p == null) {
                continue;
            }
            if (isDelivered(p) == deliveredOnly) {
                items.add(p);
            }
        }
        Collections.sort(items, new Comparator<JSONObject>() {
            @Override
            public int compare(JSONObject a, JSONObject b) {
                return Integer.compare(rank(a), rank(b));
            }
        });
        return items;
    }

    /**
     * 0 = ready to collect, 1 = in transit, 2 = to send, 3 = delivered,
     * 4 = everything else (undelivered, claimed, other).
     */
    static int rank(JSONObject p) {
        String g = p.optString("statusGroup", "").toUpperCase(Locale.US);
        String s = p.optString("status", "").toUpperCase(Locale.US);

        if (g.equals("TO_PICKUP") || g.equals("AWAITING_COLLECTION") || g.equals("READY_TO_COLLECT")
                || s.equals("AWAITING_COLLECTION") || s.equals("READY_TO_COLLECT")) {
            return 0;
        }
        if (g.equals("IN_TRANSIT") || g.equals("IN_DELIVERY")
                || s.equals("IN_TRANSIT") || s.equals("IN_DELIVERY") || s.equals("OUT_FOR_DELIVERY")) {
            return 1;
        }
        if (g.equals("TO_SEND") || g.equals("TO_SENT") || g.equals("CREATED")
                || s.equals("TO_SEND") || s.equals("CREATED")) {
            return 2;
        }
        if (g.equals("DELIVERED") || s.equals("DELIVERED")) {
            return 3;
        }
        return 4;
    }
}
