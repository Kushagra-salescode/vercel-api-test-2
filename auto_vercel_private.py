import requests
import base64
import json
import time
import os
from pathlib import Path

def load_env_file(path: Path) -> None:
    """
    Minimal env file loader (no external deps).
    Reads KEY=VALUE lines; ignores blank lines and # comments.
    Does not override variables already set in the environment.
    """
    if not path.exists() or not path.is_file():
        return

    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        key = key.strip()
        value = value.strip().strip("'").strip('"')
        if key and key not in os.environ:
            os.environ[key] = value


# Load local secrets file if present.
# Note: this repo environment blocks creating `.env*`, so we use `env.local` by default.
_here = Path(__file__).resolve().parent
load_env_file(_here / "env.local")
load_env_file(_here / ".env")  # compatibility if you create it manually


def require_env(name: str) -> str:
    val = os.environ.get(name, "").strip()
    if not val or val.startswith("PASTE_"):
        raise RuntimeError(
            f"Missing {name}. Put it in env.local (recommended) or export {name} in your shell."
        )
    return val


# ================= CONFIG =================

GITHUB_TOKEN = require_env("GITHUB_TOKEN")
VERCEL_TOKEN = require_env("VERCEL_TOKEN")

GITHUB_OWNER = os.environ.get("GITHUB_OWNER", "Kushagra-salescode")  # org or username
REPO_NAME = os.environ.get("REPO_NAME", "vercel-private-auto")

VERCEL_PROJECT_NAME = os.environ.get("VERCEL_PROJECT_NAME", "vercel-private-auto")
PRODUCTION_BRANCH = os.environ.get("PRODUCTION_BRANCH", "main")

# Set this to "org" if GITHUB_OWNER is an organization, otherwise "user"
GITHUB_OWNER_TYPE = os.environ.get("GITHUB_OWNER_TYPE", "user").strip().lower() or "user"


def github_headers():
    return {
        "Authorization": f"Bearer {GITHUB_TOKEN}",
        "Accept": "application/vnd.github+json"
    }


def create_github_repo():
    print("➡️ Creating private GitHub repo...")

    if GITHUB_OWNER_TYPE == "org":
        url = f"https://api.github.com/orgs/{GITHUB_OWNER}/repos"
    else:
        url = "https://api.github.com/user/repos"

    payload = {
        "name": REPO_NAME,
        "private": True,
        "auto_init": False
    }

    response = requests.post(
        url,
        headers=github_headers(),
        json=payload
    )

    if response.status_code not in (201, 422):
        raise Exception(f"GitHub repo creation failed: {response.text}")

    print("✅ GitHub repo ready")


def create_initial_commit():
    print("➡️ Creating initial commit (README.md)...")

    content = "# Auto-created private repo\n\nInitialized via GitHub API."
    encoded = base64.b64encode(content.encode()).decode()

    url = f"https://api.github.com/repos/{GITHUB_OWNER}/{REPO_NAME}/contents/README.md"

    payload = {
        "message": "Initial commit",
        "content": encoded,
        "branch": PRODUCTION_BRANCH
    }

    response = requests.put(
        url,
        headers=github_headers(),
        json=payload
    )

    if response.status_code not in (201, 200):
        raise Exception(f"Initial commit failed: {response.text}")

    print("✅ Repository initialized")


def create_vercel_project():
    print("➡️ Creating Vercel project...")

    url = "https://api.vercel.com/v9/projects"

    headers = {
        "Authorization": f"Bearer {VERCEL_TOKEN}",
        "Content-Type": "application/json"
    }

    payload = {
        "name": VERCEL_PROJECT_NAME,
        "gitRepository": {
            "type": "github",
            "repo": f"{GITHUB_OWNER}/{REPO_NAME}",
            "productionBranch": PRODUCTION_BRANCH
        }
    }

    response = requests.post(
        url,
        headers=headers,
        json=payload
    )

    if response.status_code not in (200, 201, 409):
        raise Exception(f"Vercel project creation failed: {response.text}")

    print("✅ Vercel project ready")


if __name__ == "__main__":
    create_github_repo()
    time.sleep(2)

    create_initial_commit()
    time.sleep(2)

    create_vercel_project()

    print("\n🎉 DONE: Private repo + Vercel project successfully created")
