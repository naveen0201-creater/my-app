# Secure Mobile Recovery & Evidence Platform (SMREP) - Deployment & Architecture Guide

Welcome to the Secure Mobile Recovery & Evidence Platform (SMREP) deployment and technical handbook. This document describes the MVVM architecture on-device, FastAPI core routing, cryptographic design boundaries, and production launch commands.

---

## 1. System Architecture Diagram

```
+-----------------------------------------------------------------------------------+
|                              SMREP MOBILE APP (Android)                           |
|                                                                                   |
|  [ MainActivity ] <--> [ MainViewModel ] <------------------------+               |
|                               |                                   |               |
|                               v                                   v               |
|                     [ PlatformRepository ]            [ Consent Onboarding ]      |
|                               | (local caching)                   |               |
|            +------------------+-------------------+               |               |
|            |                  |                   |               v               |
|            v                  v                   v          [ Permissions ]      |
|      [ Telemetry ]       [ Evidence ]      [ RecoveryEvents ]   - GPS Location    |
|      (15-min Worker)    (CameraX Captures)    (SIM/Offline Check) - Camera Lens   |
|            |                  |                   |                               |
+------------|------------------|-------------------|-------------------------------+
             | (AES-GCM)        | (AES + RSA Sign)  | (Local Logs)
             v                  v                   v
+-----------------------------------------------------------------------------------+
|                        FASTAPI HOST SERVER (Docker Backing)                       |
|                                                                                   |
|                      [ CORS / Pydantic Request Parsing ]                          |
|                                       ||                                          |
|  +--------------------+---------------+---------------+--------------------+      |
|  |                    |                               |                    |      |
|  v                    v                               v                    v      |
| [ Auth Endpoints ]  [ Telemetry Endpoints ]  [ Evidence Endpoints ] [ Lost Mode ] |
|  - /api/register     - /api/telemetry         - /api/evidence        - /enable    |
|  - /api/login                                                        - /disable   |
+-----------------------------------------------------------------------------------+
                                        ||
                                        v
+-----------------------------------------------------------------------------------+
|                        REACT + TS COLLABORATIVE PORTAL                            |
|                                                                                   |
|   - Real-time Device Binding Tables                                               |
|   - Recovery Action Logs Feed                                                     |
|   - Cryptographic Ledger Audit Log Trail Viewers                                  |
+-----------------------------------------------------------------------------------+
```

---

## 2. Secure Cryptographic System Sequence

### A. Device Identity Creation (Hashed / Privacy Safe)
1. Device reads the raw device unique payload (e.g. `Settings.Secure.ANDROID_ID`).
2. Computes `SHA-256(device_identifier)` to create a `device_hash` fingerprint.
3. The raw identity never leaves the secure boundaries of the mobile device.

### B. Secure Evidence Transport Protocol
```
[CameraX Capture File] 
        |
        v 
[Read Byte Stream] ---> [Convert Base64 Chunk]
                                |
                                v
                [Encrypt base64 via AES-256 GCM] ----> Encrypted Base64 Payload
                                |
                   (Generated AES Secret Key)
                                |
                                v
               [Wrap AES Key via RSA-2048 Server PubKey] -> Secure Wrapped Key
                                |
               [Sign Encrypted Payload via Local Private Key] -> Digital RSA Signature
                                |
                                v
             [Transmit JSON payload to FastAPI Endpoint]
```

---

## 3. Database Schema

### A. Room SQLite Shearing Sheaf

#### **Table: `users`**
*   `id` (TEXT, Primary Key): Remote owner ID hash indicator.
*   `email` (TEXT): Encoded user profile email.
*   `phone` (TEXT): Telephone recovery address.
*   `token` (TEXT): Cryptographic bearer JWT payload.
*   `registeredAt` (INTEGER): Timestamp of registration.

#### **Table: `devices`**
*   `deviceHash` (TEXT, Primary Key): SHA-256 identifier string.
*   `registeredAt` (INTEGER): Time enrolled.
*   `ownerId` (TEXT): Owner foreign relationship locator.
*   `isLostMode` (INTEGER): Flag (0 = Standby, 1 = Lost mode active).

#### **Table: `telemetry`**
*   `id` (INTEGER, Primary Key Auto-Increment)
*   `lat` (REAL): Latitude coordinate degree.
*   `lon` (REAL): Longitude coordinate degree.
*   `accuracy` (REAL): Accuracy degree.
*   `battery` (INTEGER): Remaining capacity % ratio.
*   `network` (TEXT): Connected radio capability describer.
*   `timestamp` (INTEGER): Moment recorded.
*   `isUploaded` (INTEGER): Queue sync status flag.

#### **Table: `evidence`**
*   `id` (INTEGER, Primary Key Auto-Increment)
*   `photoPath` (TEXT): File cache destination path on disk.
*   `timestamp` (INTEGER): Timestamp.
*   `isUploaded` (INTEGER): Upload sync state.
*   `signature` (TEXT): Digital RSA assertion hash.
*   `aesKeyWrapped` (TEXT): Base64 signature wrap payload.

---

## 4. Launch Instructions

### Launching backend using Docker:
```bash
# Navigate to workspace server route
cd server

# Build Docker executable container
docker build -t smrep-backend .

# Spin up immediate hosting instance on Port 8000
docker run -d -p 8000:8000 --name smrep-running smrep-backend
```

### Direct FastAPI Command (Local):
```bash
# Install dependencies
pip install fastapi uvicorn pydantic

# Launch FastAPI ASGI host
uvicorn main:app --reload --host 0.0.0.0 --port 8000
```
- **API Documentation**: Open your browser at `http://localhost:8000/docs` to view Pydantic specifications.
- **Admin Console Portal**: Open your browser at `http://localhost:8000/admin/index.html` to access the React-Tailwind Admin Console.
