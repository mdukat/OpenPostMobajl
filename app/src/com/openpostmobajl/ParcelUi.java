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

    /**
     * Pickup code: {@code openCode}, falling back to the last {@code |}-separated
     * segment of the {@code qrCode} payload (e.g. {@code P|+48...|123456}).
     */
    static String pickupCode(JSONObject p) {
        String openCode = p.optString("openCode", "");
        if (openCode.length() > 0) {
            return openCode;
        }
        String qr = p.optString("qrCode", "");
        int bar = qr.lastIndexOf('|');
        if (bar >= 0 && bar + 1 < qr.length()) {
            return qr.substring(bar + 1);
        }
        return qr;
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
     * Simplified list row for a {@link ParcelEntry}. Multi-compartment groups
     * get a "MULTISKRYTKA ..." header so they are not mistaken for one parcel.
     */
    static String simplifiedRowText(Context ctx, ParcelEntry entry) {
        String body = simplifiedRowText(ctx, entry.first);
        if (entry.isMulti()) {
            return multiCompartmentName(ctx, entry.multiUuid) + "\n" + body;
        }
        return body;
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
            case "ADOPTED_AT_SORTING_CENTER": return R.string.status_adopted_at_sorting_center;
            case "ADOPTED_AT_SOURCE_BRANCH": return R.string.status_adopted_at_source_branch;
            case "ADOPTED_AT_TARGET_BRANCH": return R.string.status_adopted_at_target_branch;
            case "AVIZO": return R.string.status_avizo;
            case "AVIZO_COMPLETED": return R.string.status_avizo_completed;
            case "AVIZO_REJECTED": return R.string.status_avizo_rejected;
            case "AWAITING_COLLECTION": return R.string.status_awaiting_collection;
            case "C2X_COMPLETED": return R.string.status_c2x_completed;
            case "C2X_REJECTED": return R.string.status_c2x_rejected;
            case "CANCELED": return R.string.status_canceled;
            case "CANCELED_REDIRECT_TO_BOX": return R.string.status_canceled_redirect_to_box;
            case "CLAIMED": return R.string.status_claimed;
            case "COD_COMPLETED": return R.string.status_cod_completed;
            case "COD_REJECTED": return R.string.status_cod_rejected;
            case "COLLECTED_BY_CUSTOMER": return R.string.status_collected_by_customer;
            case "COLLECTED_FROM_SENDER": return R.string.status_collected_from_sender;
            case "CONFIRMED": return R.string.status_confirmed;
            case "CREATED": return R.string.status_created;
            case "DELAY_IN_DELIVERY": return R.string.status_delay_in_delivery;
            case "DELIVERED": return R.string.status_delivered;
            case "DELIVERY_ATTEMPT_FAILED": return R.string.status_delivery_attempt_failed;
            case "DISPATCHED_BY_SENDER": return R.string.status_dispatched_by_sender;
            case "DISPATCHED_BY_SENDER_TO_POK": return R.string.status_dispatched_by_sender_to_pok;
            case "MISSING": return R.string.status_missing;
            case "NOT_COLLECTED": return R.string.status_not_collected;
            case "OFFERS_PREPARED": return R.string.status_offers_prepared;
            case "OFFER_SELECTED": return R.string.status_offer_selected;
            case "OTHER": return R.string.status_other;
            case "OUT_FOR_DELIVERY": return R.string.status_out_for_delivery;
            case "OUT_FOR_DELIVERY_TO_ADDRESS": return R.string.status_out_for_delivery_to_address;
            case "OVERSIZED": return R.string.status_oversized;
            case "PERMANENTLY_REDIRECTED_TO_BOX_MACHINE": return R.string.status_permanently_redirected_to_box_machine;
            case "PERMANENTLY_REDIRECTED_TO_CUSTOMER_SERVICE_POINT": return R.string.status_permanently_redirected_to_customer_service_point;
            case "PICKUP_REMINDER_SENT": return R.string.status_pickup_reminder_sent;
            case "PICKUP_REMINDER_SENT_ADDRESS": return R.string.status_pickup_reminder_sent_address;
            case "PICKUP_TIME_EXPIRED": return R.string.status_pickup_time_expired;
            case "READDRESSED": return R.string.status_readdressed;
            case "READY_FOR_COLLECTION": return R.string.status_ready_for_collection;
            case "READY_TO_COLLECT": return R.string.status_ready_to_collect;
            case "READY_TO_PICKUP": return R.string.status_ready_to_pickup;
            case "READY_TO_PICKUP_FROM_BRANCH": return R.string.status_ready_to_pickup_from_branch;
            case "READY_TO_PICKUP_FROM_POK": return R.string.status_ready_to_pickup_from_pok;
            case "READY_TO_PICKUP_FROM_POK_REGISTERED": return R.string.status_ready_to_pickup_from_pok_registered;
            case "REDIRECT_TO_BOX": return R.string.status_redirect_to_box;
            case "REJECTED_BY_RECEIVER": return R.string.status_rejected_by_receiver;
            case "RETURNED_TO_SENDER": return R.string.status_returned_to_sender;
            case "RETURN_PICKUP_CONFIRMATION_TO_SENDER": return R.string.status_return_pickup_confirmation_to_sender;
            case "SENT_FROM_SORTING_CENTER": return R.string.status_sent_from_sorting_center;
            case "SENT_FROM_SOURCE_BRANCH": return R.string.status_sent_from_source_branch;
            case "STACK_IN_BOX_MACHINE": return R.string.status_stack_in_box_machine;
            case "STACK_IN_CUSTOMER_SERVICE_POINT": return R.string.status_stack_in_customer_service_point;
            case "STACK_PARCEL_IN_BOX_MACHINE_PICKUP_TIME_EXPIRED": return R.string.status_stack_parcel_in_box_machine_pickup_time_expired;
            case "STACK_PARCEL_PICKUP_TIME_EXPIRED": return R.string.status_stack_parcel_pickup_time_expired;
            case "TAKEN_BY_COURIER": return R.string.status_taken_by_courier;
            case "TAKEN_BY_COURIER_FROM_POK": return R.string.status_taken_by_courier_from_pok;
            case "UNDELIVERED": return R.string.status_undelivered;
            case "UNDELIVERED_COD_CASH_RECEIVER": return R.string.status_undelivered_cod_cash_receiver;
            case "UNDELIVERED_INCOMPLETE_ADDRESS": return R.string.status_undelivered_incomplete_address;
            case "UNDELIVERED_LACK_OF_ACCESS_LETTERBOX": return R.string.status_undelivered_lack_of_access_letterbox;
            case "UNDELIVERED_NOT_LIVE_ADDRESS": return R.string.status_undelivered_not_live_address;
            case "UNDELIVERED_NO_MAILBOX": return R.string.status_undelivered_no_mailbox;
            case "UNDELIVERED_UNKNOWN_RECEIVER": return R.string.status_undelivered_unknown_receiver;
            case "UNDELIVERED_WRONG_ADDRESS": return R.string.status_undelivered_wrong_address;
            case "UNSTACK_FROM_BOX_MACHINE": return R.string.status_unstack_from_box_machine;
            case "UNSTACK_FROM_CUSTOMER_SERVICE_POINT": return R.string.status_unstack_from_customer_service_point;
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
            case "OTHER":
                return R.string.status_group_other;
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
