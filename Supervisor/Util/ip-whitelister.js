import dotenv from "dotenv";
dotenv.config({ path: process.env.NODE_ENV === "production" ? "/etc/msu/.env" : "./.env" });

const USE_MOCK_LAMBDA = process.env.NODE_ENV !== "production";

import { LambdaClient, InvokeCommand } from "@aws-sdk/client-lambda";

/*
 * Calls AWS Lambda to modify EC2 firewall (authorize or deauthorize IP).
 */
export async function invokeFirewallLambda(action, ip, username) {
  if(USE_MOCK_LAMBDA){
    return {ok: true, ip: ip, username: username};
  }

  const REGION = process.env.AWS_REGION;
  const LAMBDA_FUNCTION_NAME = process.env.FIREWALL_LAMBDA_NAME;

  const client = new LambdaClient({ region: REGION });

  const payload = {
    action,
    ip,
    username,
  };

  try {
    const command = new InvokeCommand({
      FunctionName: LAMBDA_FUNCTION_NAME,
      InvocationType: "RequestResponse", // Wait for result
      Payload: Buffer.from(JSON.stringify(payload)),
    });

    const response = await client.send(command);

    const resultString = Buffer.from(response.Payload).toString();
    const result = JSON.parse(resultString);

    if (!result.ok) {
      throw new Error(result.error || "Unknown Lambda error");
    }

    console.log(
      `Lambda (${action}) success for ${username} (${ip}): ${result.message}`
    );

    return result;
  } catch (err) {
    console.error("Lambda invoke failed:", err.message);
    return { ok: false, error: err.message };
  }
}