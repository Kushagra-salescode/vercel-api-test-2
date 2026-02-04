import requests #to make HTTP calls
import json #to convert Python dict → JSON

VERCEL_TOKEN = "2b5QKtQVQCEYnd7oV6Yvxzow"

PROJECT_NAME = "my-vercel-project-2"
GIT_PROVIDER = "github"  # github | gitlab | bitbucket
GIT_REPO = "Kushagra-salescode/vercel-api-test-2"
PRODUCTION_BRANCH = "main"

TEAM_ID = None  # keep None unless using a team


# API URL & headers
url = "https://api.vercel.com/v9/projects"

headers = {
    "Authorization": f"Bearer {VERCEL_TOKEN}",
    "Content-Type": "application/json"
}

# Request payload
payload = {
    "name": PROJECT_NAME,
    "gitRepository": { #links Git repo
        "type": GIT_PROVIDER,
        "repo": GIT_REPO,
        "productionBranch": PRODUCTION_BRANCH
    },
    "framework": None,
    # "rootDirectory": ".", #useful for monorepos 
    "buildCommand": None,
    "installCommand": None,
    "outputDirectory": None
}

# Optional team support
params = {}
if TEAM_ID:
    params["teamId"] = TEAM_ID #adds ?teamId=xxx to the URL if needed


# Make the API call -> actual moment where Python talks to Vercel servers
response = requests.post(
    url,
    headers=headers,
    params=params,
    data=json.dumps(payload)
)

# Handle the response
if response.status_code in (200, 201):
    print("Project created successfully")
    print(json.dumps(response.json(), indent=2))
else:
    print("Failed to create project")
    print(response.status_code, response.text)