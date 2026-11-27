---
title: MuE-Air Sidecar
sdk: docker
app_port: 7860
---

# MuE-Air Sidecar

FastAPI service powering YouTube Music and Spotify playlist imports for MuE-Air.
Resolves public/private playlists and maps Spotify tracks to playable YouTube Music `videoId`s.

## Required Environment Variables
- `GOOGLE_CLIENT_ID` — Google OAuth Client ID (for private YouTube Music library sync)
- `GOOGLE_CLIENT_SECRET` — Google OAuth Client Secret

## Health Check
- `GET /health` → `200 {"status": "ok"}`
