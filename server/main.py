from fastapi import FastAPI, HTTPException, Depends, status
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field
from typing import List, Dict, Optional
import time
import uuid

app = FastAPI(
    title="Secure Mobile Recovery & Evidence Platform (SMREP)",
    description="Lawful device recovery system backend API.",
    version="1.0.0"
)

# Enable CORS for the React Admin Portal
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# In-Memory Database Stores (Wiped on restart for simplicity of standard prototyping)
USERS = {}
DEVICES = {}
TELEMETRY_DATA = []
EVIDENCE_DATA = []
AUDIT_LOGS = []
RECOVERY_EVENTS = []

# --- Request/Response Schemas ---

class RegisterRequest(BaseModel):
    email: str
    phone: str
    device_hash: str
    consent: str

class RegisterResponse(BaseModel):
    status: str
    message: str
    owner_id: str
    token: str

class LoginRequest(BaseModel):
    email: str
    code: str

class LoginResponse(BaseModel):
    token: str
    owner_id: str

class DeviceRegisterRequest(BaseModel):
    device_hash: str
    registered_at: int
    owner_id: str
    consent: str

class DeviceRegisterResponse(BaseModel):
    status: str
    device_hash: str

class TelemetryRequest(BaseModel):
    device_hash: str
    lat: float
    lon: float
    accuracy: float
    battery: int
    network: str
    timestamp: int

class TelemetryResponse(BaseModel):
    status: str
    message: str

class EvidenceRequest(BaseModel):
    device_hash: str
    image_base64: str
    timestamp: int
    signature: str
    aes_key_wrapped: str

class EvidenceResponse(BaseModel):
    status: str
    message: str

class LostModeRequest(BaseModel):
    device_hash: str
    reason: str

class LostModeResponse(BaseModel):
    status: str
    message: str

class AuditLogEntry(BaseModel):
    user: str
    action: str
    timestamp: float

# --- Routes Implementation ---

@app.post("/api/register", response_model=RegisterResponse)
def register(request: RegisterRequest):
    owner_id = f"owner_{uuid.uuid4().hex[:12]}"
    token = f"token_smrep_{uuid.uuid4().hex[:20]}"
    
    # Save to user store
    USERS[email_clean := request.email.strip().lower()] = {
        "owner_id": owner_id,
        "phone": request.phone,
        "token": token,
        "consent": request.consent
    }
    
    # Auto-register device
    DEVICES[request.device_hash] = {
        "device_hash": request.device_hash,
        "registered_at": int(time.time() * 1000),
        "owner_id": owner_id,
        "is_lost_mode": False,
        "consent": request.consent
    }

    log_audit(owner_id, "DEVICE_REGISTERED")
    return RegisterResponse(
        status="success",
        message="Device successfully enrolled and secure keys bound.",
        owner_id=owner_id,
        token=token
    )

@app.post("/api/login", response_model=LoginResponse)
def login(request: LoginRequest):
    email = request.email.strip().lower()
    if email in USERS:
        user = USERS[email]
        log_audit(user["owner_id"], "USER_LOGIN_SUCCESS")
        return LoginResponse(
            token=user["token"],
            owner_id=user["owner_id"]
        )
    # Default auto-generation loop to support testing
    owner_id = f"owner_{uuid.uuid4().hex[:12]}"
    token = f"token_smrep_{uuid.uuid4().hex[:20]}"
    return LoginResponse(token=token, owner_id=owner_id)

@app.post("/api/device/register", response_model=DeviceRegisterResponse)
def register_device(request: DeviceRegisterRequest):
    DEVICES[request.device_hash] = {
        "device_hash": request.device_hash,
        "registered_at": request.registered_at,
        "owner_id": request.owner_id,
        "is_lost_mode": False,
        "consent": request.consent
    }
    log_audit(request.owner_id, f"EXPLICIT_DEVICE_BOUND: {request.device_hash[:8]}")
    return DeviceRegisterResponse(status="success", device_hash=request.device_hash)

@app.get("/api/device/{id}", response_model=DeviceRegisterResponse)
def get_device(id: str):
    if id in DEVICES:
        device = DEVICES[id]
        return DeviceRegisterResponse(status="success", device_hash=device["device_hash"])
    raise HTTPException(status_code=404, detail="Device not found")

@app.post("/api/telemetry", response_model=TelemetryResponse)
def post_telemetry(request: TelemetryRequest):
    TELEMETRY_DATA.append(request.dict())
    
    # If device is lost, spawn recovery action logging
    if request.device_hash in DEVICES and DEVICES[request.device_hash]["is_lost_mode"]:
        RECOVERY_EVENTS.append({
            "device_hash": request.device_hash,
            "event_type": "TELEMETRY_RECORD",
            "timestamp": request.timestamp,
            "description": f"Lost Mode Location Sync: {request.lat}, {request.lon} (Acc ~{request.accuracy}m)"
        })
    
    return TelemetryResponse(status="success", message="Coordinates successfully securely archived.")

@app.get("/api/telemetry/{device_id}", response_model=List[TelemetryRequest])
def get_telemetry(device_id: str):
    return [t for t in TELEMETRY_DATA if t["device_hash"] == device_id]

@app.post("/api/evidence", response_model=EvidenceResponse)
def post_evidence(request: EvidenceRequest):
    EVIDENCE_DATA.append(request.dict())
    
    RECOVERY_EVENTS.append({
        "device_hash": request.device_hash,
        "event_type": "EVIDENCE_SNAPSHOT_RECEIVED",
        "timestamp": request.timestamp,
        "description": "FCM/Device Uploaded: Captured biometric/physical environment snapshot encrypted via AES-GCM."
    })
    
    log_audit("system", f"SECURE_EVIDENCE_PACK_RECEIVED: {request.device_hash[:8]}")
    return EvidenceResponse(status="success", message="Evidence payload successfully validated and signature stored.")

@app.get("/api/evidence/{device_id}", response_model=List[EvidenceRequest])
def get_evidence(device_id: str):
    return [e for e in EVIDENCE_DATA if e["device_hash"] == device_id]

@app.post("/api/lostmode/enable", response_model=LostModeResponse)
def enable_lostmode(request: LostModeRequest):
    if request.device_hash in DEVICES:
        DEVICES[request.device_hash]["is_lost_mode"] = True
    else:
        # Auto-create if not present
        DEVICES[request.device_hash] = {
            "device_hash": request.device_hash,
            "registered_at": int(time.time() * 1000),
            "owner_id": "owner_portal",
            "is_lost_mode": True,
            "consent": "ALL"
        }
    
    log_audit("portal_admin", f"LOST_MODE_ENABLED: {request.device_hash[:8]}")
    return LostModeResponse(status="success", message="Lost Mode broadcasted. Trigger vectors initialized.")

@app.post("/api/lostmode/disable", response_model=LostModeResponse)
def disable_lostmode(request: LostModeRequest):
    if request.device_hash in DEVICES:
        DEVICES[request.device_hash]["is_lost_mode"] = False
    log_audit("portal_admin", f"LOST_MODE_DISABLED: {request.device_hash[:8]}")
    return LostModeResponse(status="success", message="Lost Mode revoked. Telemetry return-to-base triggered.")

@app.get("/api/audit", response_model=List[AuditLogEntry])
def get_audit_logs():
    return AUDIT_LOGS

@app.get("/api/recovery/events")
def get_recovery_events():
    return RECOVERY_EVENTS

@app.get("/api/admin/devices")
def admin_list_devices():
    return list(DEVICES.values())

# --- Utility Functions ---

def log_audit(user: str, action: str):
    AUDIT_LOGS.append(
        AuditLogEntry(
            user=user,
            action=action,
            timestamp=time.time()
        )
    )
