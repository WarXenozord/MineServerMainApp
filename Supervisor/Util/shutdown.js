import dotenv from "dotenv";
dotenv.config({ path: process.env.NODE_ENV === "production" ? "/etc/msu/.env" : "./.env" });

import { EC2Client, StopInstancesCommand } from "@aws-sdk/client-ec2";

const client = new EC2Client({ region: process.env.AWS_REGION });
const INSTANCE_ID = process.env.INSTANCE_ID;

export async function turnOff(){
    await client.send(new StopInstancesCommand({
    InstanceIds: [INSTANCE_ID],
    }));
}