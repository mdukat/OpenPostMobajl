package com.openpostmobajl;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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

    static boolean isReadyToPickup(JSONObject p) {
        String g = p.optString("statusGroup", "").toUpperCase(Locale.US);
        String s = p.optString("status", "").toUpperCase(Locale.US);
        return g.equals("TO_PICKUP") || g.equals("AWAITING_COLLECTION")
                || g.equals("READY_TO_COLLECT")
                || s.equals("READY_TO_PICKUP") || s.equals("AWAITING_COLLECTION")
                || s.equals("READY_TO_COLLECT");
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

    /**
     * uuid of the parcel's {@code multiCompartment} block when the parcel is
     * ready to pick up and the block is flagged for presentation, else
     * {@code null}.
     */
    static String multiCompartmentUuid(JSONObject p) {
        if (!isReadyToPickup(p)) {
            return null;
        }
        JSONObject mc = p.optJSONObject("multiCompartment");
        if (mc == null || !mc.optBoolean("presentation", false)) {
            return null;
        }
        String uuid = mc.optString("uuid", "");
        return uuid.length() == 0 ? null : uuid;
    }

    /** Human name for a multi-compartment group: {@code MULTISKRYTKA <uuid-section>}. */
    static String multiCompartmentName(String uuid) {
        int dash = uuid.indexOf('-');
        String section = dash > 0 ? uuid.substring(0, dash) : uuid;
        return "MULTISKRYTKA " + section;
    }

    static String multiCompartmentRowText(ParcelEntry e) {
        return multiCompartmentName(e.multiUuid)
                + "  \u00b7  " + e.parcels.size() + " parcels"
                + "  \u00b7  " + pointName(e.first)
                + "  \u00b7  " + senderName(e.first)
                + "  \u203a";
    }

    /**
     * Collapses ready-to-pickup parcels that share a presentable
     * {@code multiCompartment.uuid} into a single {@link ParcelEntry}. Only
     * uuids seen on more than one parcel are grouped; everything else stays a
     * single-parcel entry. The original scan order is preserved and the first
     * member becomes the group representative.
     */
    static List<ParcelEntry> grouped(List<JSONObject> sorted) {
        Map<String, List<JSONObject>> byUuid = new LinkedHashMap<String, List<JSONObject>>();
        for (int i = 0; i < sorted.size(); i++) {
            JSONObject p = sorted.get(i);
            String uuid = multiCompartmentUuid(p);
            if (uuid == null) {
                continue;
            }
            List<JSONObject> members = byUuid.get(uuid);
            if (members == null) {
                members = new ArrayList<JSONObject>();
                byUuid.put(uuid, members);
            }
            members.add(p);
        }

        Set<String> multi = new HashSet<String>();
        for (Map.Entry<String, List<JSONObject>> e : byUuid.entrySet()) {
            if (e.getValue().size() > 1) {
                multi.add(e.getKey());
            }
        }

        List<ParcelEntry> entries = new ArrayList<ParcelEntry>();
        Set<String> emitted = new HashSet<String>();
        for (int i = 0; i < sorted.size(); i++) {
            JSONObject p = sorted.get(i);
            String uuid = multiCompartmentUuid(p);
            if (uuid != null && multi.contains(uuid)) {
                if (emitted.add(uuid)) {
                    entries.add(ParcelEntry.group(byUuid.get(uuid), uuid));
                }
            } else {
                entries.add(ParcelEntry.single(p));
            }
        }
        return entries;
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
