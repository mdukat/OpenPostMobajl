package com.openpostmobajl;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * One row in the parcel list: either a single parcel, or a logical
 * "MULTISKRYTKA" group of ready-to-pickup parcels that share one
 * {@code multiCompartment.uuid} (with {@code presentation = true}).
 */
final class ParcelEntry {

    /** Representative parcel, i.e. the first one in scan order. */
    final JSONObject first;

    /** All parcels of this entry; one element for a normal, ungrouped parcel. */
    final List<JSONObject> parcels;

    /** {@code multiCompartment.uuid} for a group, otherwise {@code null}. */
    final String multiUuid;

    private ParcelEntry(JSONObject first, List<JSONObject> parcels, String multiUuid) {
        this.first = first;
        this.parcels = parcels;
        this.multiUuid = multiUuid;
    }

    static ParcelEntry single(JSONObject parcel) {
        List<JSONObject> one = new ArrayList<JSONObject>();
        one.add(parcel);
        return new ParcelEntry(parcel, one, null);
    }

    static ParcelEntry group(List<JSONObject> members, String uuid) {
        return new ParcelEntry(members.get(0), members, uuid);
    }

    boolean isMulti() {
        return multiUuid != null && parcels.size() > 1;
    }
}
