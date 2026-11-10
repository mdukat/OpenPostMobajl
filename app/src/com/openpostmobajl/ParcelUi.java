package com.openpostmobajl;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

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

    /** Simplified-mode list row: {@code "Parcel: <sender>\n<status>"}. */
    static String simplifiedRowText(Context ctx, JSONObject p) {
        String sender = senderName(p);
        if (sender.length() == 0) {
            sender = last6(p);
        }
        String text = ctx.getString(R.string.main_parcel_simplified, sender);
        String status = statusText(ctx, p.optString("status", ""),
                p.optString("statusGroup", ""));
        if (status.length() > 0) {
            text += "\n" + status;
        }
        return text;
    }

    /**
     * Human status for simplified mode, e.g.
     * {@code "Taken by courier - parcel in transit"}. Unknown codes are
     * prettified ({@code SOME_CODE} -&gt; {@code Some code}).
     */
    static String statusText(Context ctx, String status, String group) {
        String s = statusLabel(ctx, status);
        String g = groupLabel(ctx, group);
        if (s.length() == 0) {
            return g;
        }
        if (g.length() == 0 || s.equalsIgnoreCase(g)) {
            return s;
        }
        return s + " - " + g;
    }

    static String statusLabel(Context ctx, String code) {
        int id = statusStringId(code);
        return id != 0 ? ctx.getString(id) : prettify(code);
    }

    static String groupLabel(Context ctx, String code) {
        int id = groupStringId(code);
        return id != 0 ? ctx.getString(id) : prettify(code);
    }

    private static int statusStringId(String code) {
        if (code == null) {
            return 0;
        }
        switch (code.toUpperCase(Locale.US)) {
            case "CONFIRMED": return R.string.status_confirmed;
            case "DISPATCHED_BY_SENDER": return R.string.status_dispatched_by_sender;
            case "TAKEN_BY_COURIER": return R.string.status_taken_by_courier;
            case "READY_TO_PICKUP": return R.string.status_ready_to_pickup;
            case "AWAITING_COLLECTION": return R.string.status_awaiting_collection;
            case "READY_TO_COLLECT": return R.string.status_ready_to_collect;
            case "OUT_FOR_DELIVERY": return R.string.status_out_for_delivery;
            case "DELIVERED": return R.string.status_delivered;
            case "UNDELIVERED": return R.string.status_undelivered;
            case "CLAIMED": return R.string.status_claimed;
            default: return 0;
        }
    }

    private static int groupStringId(String code) {
        if (code == null) {
            return 0;
        }
        switch (code.toUpperCase(Locale.US)) {
            case "TO_SEND":
            case "TO_SENT":
                return R.string.status_group_to_send;
            case "CREATED":
                return R.string.status_group_created;
            case "IN_TRANSIT":
                return R.string.status_group_in_transit;
            case "IN_DELIVERY":
                return R.string.status_group_in_delivery;
            case "TO_PICKUP":
            case "AWAITING_COLLECTION":
            case "READY_TO_COLLECT":
                return R.string.status_group_to_pickup;
            case "DELIVERED":
                return R.string.status_group_delivered;
            case "UNDELIVERED":
                return R.string.status_group_undelivered;
            case "CLAIMED":
                return R.string.status_group_claimed;
            default: return 0;
        }
    }

    private static String prettify(String code) {
        if (code == null || code.length() == 0) {
            return "";
        }
        String s = code.replace('_', ' ').toLowerCase(Locale.US);
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /**
     * Multi-line pickup point for simplified mode: name, location description,
     * opening hours and the address. Empty lines are skipped.
     */
    static String pickupDetails(JSONObject p) {
        JSONObject o = p.optJSONObject("pickUpPoint");
        if (o == null) {
            o = p.optJSONObject("pickUp");
        }
        if (o == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        appendLine(sb, o.optString("name", ""));
        appendLine(sb, o.optString("locationDescription", ""));
        appendLine(sb, o.optString("openingHours", ""));
        JSONObject a = o.optJSONObject("addressDetails");
        if (a != null) {
            StringBuilder addr = new StringBuilder();
            String street = a.optString("street", "");
            String building = a.optString("buildingNumber", "");
            if (street.length() > 0) {
                addr.append(street);
            }
            if (building.length() > 0) {
                if (addr.length() > 0) {
                    addr.append(' ');
                }
                addr.append(building);
            }
            String post = a.optString("postCode", "");
            String city = a.optString("city", "");
            if (post.length() > 0 || city.length() > 0) {
                if (addr.length() > 0) {
                    addr.append(", ");
                }
                if (post.length() > 0) {
                    addr.append(post);
                    if (city.length() > 0) {
                        addr.append(' ');
                    }
                }
                if (city.length() > 0) {
                    addr.append(city);
                }
            }
            appendLine(sb, addr.toString());
        }
        return sb.toString();
    }

    private static void appendLine(StringBuilder sb, String line) {
        if (line == null || line.length() == 0) {
            return;
        }
        if (sb.length() > 0) {
            sb.append('\n');
        }
        sb.append(line);
    }

    /**
     * Epoch millis of a ready-to-pickup parcel's {@code expiryDate}, or 0 when
     * the parcel is not ready to pick up or has no valid date.
     */
    static long expiryMillis(JSONObject p) {
        if (!isReadyToPickup(p)) {
            return 0;
        }
        return parseIsoMillis(p.optString("expiryDate", ""));
    }

    /** Epoch millis of a parcel's {@code storedDate}, or 0 if absent/invalid. */
    static long storedMillis(JSONObject p) {
        return parseIsoMillis(p.optString("storedDate", ""));
    }

    static long parseIsoMillis(String s) {
        if (s == null || s.length() == 0) {
            return 0;
        }
        String[] patterns = {
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
                "yyyy-MM-dd'T'HH:mm:ssXXX"
        };
        for (int i = 0; i < patterns.length; i++) {
            try {
                SimpleDateFormat f = new SimpleDateFormat(patterns[i], Locale.US);
                f.setTimeZone(TimeZone.getTimeZone("UTC"));
                f.setLenient(false);
                return f.parse(s).getTime();
            } catch (Exception ignored) {
            }
        }
        return 0;
    }

    /**
     * Human-readable expiry: the local date/time plus the remaining time, for
     * example {@code "Expires 2026-12-24 08:37 · in 3d 4h"} or
     * {@code "Expires 2026-12-24 08:37 · expired"}.
     */
    static String expiryText(Context ctx, long expiryMillis) {
        String when = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
                .format(new Date(expiryMillis));
        long remaining = expiryMillis - System.currentTimeMillis();
        if (remaining <= 0) {
            return ctx.getString(R.string.expires_past, when);
        }
        long totalSec = remaining / 1000;
        long days = totalSec / 86400;
        long hours = (totalSec % 86400) / 3600;
        long minutes = (totalSec % 3600) / 60;
        long seconds = totalSec % 60;
        String left;
        if (days > 0) {
            left = days + "d " + hours + "h";
        } else if (hours > 0) {
            left = hours + "h " + minutes + "m";
        } else if (minutes > 0) {
            left = minutes + "m " + seconds + "s";
        } else {
            left = seconds + "s";
        }
        return ctx.getString(R.string.expires_in, when, left);
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
    static String multiCompartmentName(Context ctx, String uuid) {
        int dash = uuid.indexOf('-');
        String section = dash > 0 ? uuid.substring(0, dash) : uuid;
        return ctx.getString(R.string.multi_name, section);
    }

    static String multiCompartmentRowText(Context ctx, ParcelEntry e) {
        String count = ctx.getResources().getQuantityString(
                R.plurals.multi_parcels, e.parcels.size(), e.parcels.size());
        return multiCompartmentName(ctx, e.multiUuid)
                + "  \u00b7  " + count
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
     * @param deliveredOnly true = only delivered parcels, newest
     *                      {@code storedDate} first; false = only
     *                      non-delivered parcels, sorted by status rank.
     */
    static List<JSONObject> sorted(JSONArray parcels, final boolean deliveredOnly) {
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
                if (deliveredOnly) {
                    // Newest stored first; a missing/invalid storedDate (0) last.
                    return Long.compare(storedMillis(b), storedMillis(a));
                }
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
