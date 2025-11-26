# MineServerMainApp

This project bundles a lightweight Node.js supervisor/authorizer with a Paper server configured with SkinRestorer and a custom authentication plugin built for safe offline operation.
It’s intended to run on an EC2 instance that stays offline most of the time and spins up only when requested by the companion web app:

👉 https://github.com/WarXenozord/MineServerWebApp

## Supervisor

The Node.js supervisor exposes an API used by both the Minecraft plugin and the web app.
Its responsibilities:

* Authorize and revoke player IPs by modifying the EC2 security group through Lambda
* Manage login grace periods
* Shut down the EC2 instance when no players remain online for a defined timeout

## Minecraft Server

The `Minecraft` folder scripts that install a Paper server and build the custom authentication plugin. The authenticator provides an in-game login system with restoration of position, inventory, skin, and name for offline mode. It also exposes an API that reports currently logged-in players and their IPs to the supervisor, and notifies the supervisor when players disconnect so it can remove their IP from the firewall whitelist.

## 🚀 Getting Started

### 1. Clone the repo

```bash
git clone https://github.com/WarXenozord/MineServerMainApp.git
cd MineServerMainApp
```

### 2. Install the Paper server

```bash
npm run setup:server
```

### 3. Configure Paper

Adjust the file:

```
Minecraft/server/server.properties
```

Choose your seed, difficulty, and other preferences.

### 4. Run the server

```bash
npm run run:server
```

### 5. Set up the Supervisor

```bash
cd Supervisor
npm install
```

### 6. Configure environment variables

Copy `.env.template`, edit it, and inject it into `process.env`.

### 7. Start the Supervisor

```bash
npm run start
```


## 🔄 Setting the EC2 / Lambdas

Note: required EC2 power depends on player count.  
Example: a `t3a.medium` handles ~4–6 players without trouble.

### 1. Deploy the Lambda functions

Everything is inside the `Lambdas/` folder.

### 2. Grant permissions to the EC2 instance

Allow the instance to call the Lambda functions for IP authorization / IP revocation  

### 3. Allow the EC2 instance to shut itself down

Add the correct IAM policy (usually `ec2:StopInstances` for that instance only).

### 4. Whitelist the Web App SG

Add the Web App Security Group to the Supervisor API port inbound rules so the EC2 running the web server can talk to this supervisor.
