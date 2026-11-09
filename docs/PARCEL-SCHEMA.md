# Parcel schema (PL v4 API)

The app uses the current PL endpoint:

```
GET https://api-inmobile-pl.easypack24.net/v4/parcels/tracked
Authorization: Bearer <access_token>
X-App-Id: pl.inpost.inmobile
device-uid: <stable UUID>
User-Agent: InPost-Mobile/4.22.1(42201000) (Android 15; Pixel 7; google panther; pl)
Accept-Language: pl-PL
Accept: application/json
Content-Type: application/json
```

The newer global CPS endpoint
(`/global/cps/api/v1/parcels?role=receiver`) is disabled for the PL market
(HTTP 501 `FEATURE_DISABLED`) and has a different schema.

## Top-level response

```json
{
  "updatedUntil": "2026-11-09T13:24:39.385Z",
  "more": false,
  "parcels": [ ... ]
}
```

- `updatedUntil` — server timestamp of this snapshot.
- `more` — pagination flag (false for the v4 tracked list).
- `parcels` — array of parcel objects.

## Example parcel (IN_TRANSIT, personal fields redacted)

```json
{
  "shipmentNumber": "6209...",
  "shipmentType": "parcel",
  "pickUpPoint": {
    "name": "OTW13M",
    "location": { "latitude": 52.11078, "longitude": 21.26401 },
    "locationDescription": "Przed sklepem Społem po prawej stronie",
    "openingHours": "24/7",
    "addressDetails": {
      "postCode": "05-402",
      "city": "Otwock",
      "province": "mazowieckie",
      "street": "Władysława Czaplickiego",
      "buildingNumber": "2",
      "country": "PL"
    },
    "virtual": 0,
    "pointType": "PL",
    "type": ["parcel_locker"],
    "location247": true,
    "doubled": false,
    "imageUrl": "https://static.easypack24.net/points/pl/images/OTW13M.jpg",
    "easyAccessZone": true,
    "airSensor": false,
    "country": "PL"
  },
  "endOfWeekCollection": false,
  "operations": {
    "manualArchive": false,
    "delete": true,
    "collect": false,
    "expandAvizo": false,
    "highlight": false,
    "refreshUntil": "2026-12-24T07:37:41.788Z",
    "requestEasyAccessZone": "ALLOWED",
    "voicebot": true,
    "chatbot": true,
    "canShareToObserve": true,
    "canShareOpenCode": false,
    "canShareParcel": true,
    "withDonations": false
  },
  "status": "DISPATCHED_BY_SENDER",
  "statusGroup": "IN_TRANSIT",
  "eventLog": [
    { "type": "PARCEL_STATUS", "name": "DISPATCHED_BY_SENDER", "date": "2026-11-09T10:02:38.271Z" },
    { "type": "PARCEL_STATUS", "name": "CONFIRMED", "date": "2026-11-09T07:37:41.773Z" }
  ],
  "avizoTransactionStatus": "NONE",
  "ownershipStatus": "OWN",
  "economyParcel": false,
  "internationalParcel": false,
  "parcelSize": "A",
  "receiver": {
    "phoneNumber": { "prefix": "+48", "value": "***" },
    "email": "***@***",
    "name": "***"
  },
  "sender": { "name": "***" },
  "events": [
    {
      "date": "2026-11-09T10:02:38.271Z",
      "eventTitle": "Nadana przez Paczkomat/PaczkoPunkt",
      "eventDescription": "Paczka została nadana przez Nadawcę i już wkrótce do Ciebie trafi.",
      "eventCode": "FMD.1001"
    },
    {
      "date": "2026-11-09T07:37:41.773Z",
      "eventTitle": "Przesyłka utworzona",
      "eventDescription": "Przesyłka została utworzona, ale nie jest gotowa do nadania.",
      "eventCode": "CRE.1001"
    }
  ],
  "sharedTo": [],
  "parcelId": "00000000-0000-0000-0000-000000000000"
}
```

## Field reference

| Field | Type | Meaning |
|---|---|---|
| `shipmentNumber` | string | Human-facing tracking number. |
| `parcelId` | string (UUID) | Internal parcel ID used by the global CPS API. |
| `shipmentType` | string | `parcel` (other values possible). |
| `status` | string | Granular status, e.g. `CONFIRMED`, `DISPATCHED_BY_SENDER`, `TAKEN_BY_COURIER`, `READY_TO_PICKUP`, `OUT_FOR_DELIVERY`, `DELIVERED`. |
| `statusGroup` | string | Category used for sorting: `TO_SEND`, `IN_TRANSIT`, `TO_PICKUP`, `DELIVERED`, `UNDELIVERED`, `CLAIMED`, `OTHER`. |
| `openCode` | string? | Pickup code; present when the parcel is ready/delivered. |
| `qrCode` | string? | QR payload; present when the parcel is ready/delivered. |
| `storedDate` | ISO string? | When the parcel was stored in the locker. |
| `pickUpDate` | ISO string? | When the parcel was collected. |
| `expiryDate` | ISO string? | When the parcel expires (not always present). |
| `returnedToSenderDate` | ISO string? | When the parcel was returned (not always present). |
| `pickUpPoint` | object? | Locker/point details (see below). |
| `multiCompartment` | object? | Multi-compartment details (see below). |
| `endOfWeekCollection` | bool | End-of-week collection flag. |
| `operations` | object | Allowed actions (see below). |
| `eventLog` | array | Machine-readable status history. |
| `events` | array | Localized human-readable history. |
| `avizoTransactionStatus` | string | `NONE` or avizo status. |
| `ownershipStatus` | string | `OWN`, `SHARED`, etc. |
| `economyParcel` | bool | Economy parcel flag. |
| `internationalParcel` | bool | International parcel flag. |
| `parcelSize` | string | `A`, `B`, `C`, `D`. |
| `receiver` | object | `phoneNumber {prefix, value}`, `email`, `name`. |
| `sender` | object | `name`. |
| `sharedTo` | array | Friends the parcel is shared with. |

### `pickUpPoint`

`name`, `location {latitude, longitude}`, `locationDescription`,
`openingHours`, `addressDetails {postCode, city, province, street,
buildingNumber, country}`, `virtual`, `pointType`, `type[]` (e.g.
`parcel_locker`), `location247`, `doubled`, `imageUrl`, `easyAccessZone`,
`airSensor`, `country`.

### `operations`

`manualArchive`, `autoArchivableSince`, `delete`, `collect`, `expandAvizo`,
`highlight`, `refreshUntil`, `requestEasyAccessZone` (`ALLOWED` /
`DISALLOWED`), `voicebot`, `chatbot`, `canShareToObserve`, `canShareOpenCode`,
`canShareParcel`, `withDonations`.

### `events[]`

`date`, `eventTitle`, `eventDescription`, `eventCode` (e.g. `CRE.1001`,
`FMD.1001`, `LMD.1005`, `EOL.1001`).

### `eventLog[]`

`type` (`PARCEL_STATUS`), `name` (status code), `date`, optional `details`.

### `multiCompartment`

`uuid`, `presentation`, `collected`, optional `shipmentNumbers[]`.

## Status groups

| `statusGroup` | Meaning | Typical `status` values |
|---|---|---|
| `TO_SEND` | Created/confirmed, not handed over yet | `CONFIRMED` |
| `IN_TRANSIT` | Handed over / on the way | `DISPATCHED_BY_SENDER`, `TAKEN_BY_COURIER` |
| `TO_PICKUP` | Ready to collect | `READY_TO_PICKUP`, `AWAITING_COLLECTION` |
| `DELIVERED` | Collected/delivered | `DELIVERED` |
| `UNDELIVERED` | Delivery failed | — |
| `CLAIMED` | Claimed | — |
| `OTHER` | Anything else | — |

The app's own `ShipmentStatusGroup` enum is:
`CREATED`, `TO_SENT` (`TO_SEND`), `IN_TRANSIT`, `IN_DELIVERY`, `TO_PICKUP`,
`DELIVERED`, `UNDELIVERED`, `CLAIMED`, `OTHER`.

## Delivered-only fields

A delivered parcel additionally carries:

- `openCode`, `qrCode`
- `storedDate`, `pickUpDate`
- `multiCompartment`
- `operations.manualArchive = true`
- `operations.autoArchivableSince`
- `operations.requestEasyAccessZone = "DISALLOWED"`
- `operations.withDonations` (may be `true`)

`TO_SEND` and `IN_TRANSIT` parcels usually omit `openCode`, `qrCode`,
`storedDate`, `pickUpDate`, and `multiCompartment`.
