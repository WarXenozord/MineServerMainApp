import { EC2Client, AuthorizeSecurityGroupIngressCommand, RevokeSecurityGroupIngressCommand } from "@aws-sdk/client-ec2";

const ec2 = new EC2Client({ region: process.env.AWS_REGION });
const SG_ID = process.env.MC_SECURITY_GROUP_ID;
const MC_PORT = parseInt(process.env.MC_PORT || "25565");

export const handler = async (event) => {
  const { action, ip, username } = event;

  if (!ip) {
    return { ok: false, error: "Missing IP" };
  }

  const params = {
    GroupId: SG_ID,
    IpPermissions: [
      {
        IpProtocol: "tcp",
        FromPort: MC_PORT,
        ToPort: MC_PORT,
        IpRanges: [
          {
            CidrIp: `${ip}/32`,
            Description: `MC access for ${username || "unknown"}`,
          },
        ],
      },
    ],
  };

  try {
    if (action === "authorize") {
      await ec2.send(new AuthorizeSecurityGroupIngressCommand(params));
      return { ok: true, message: `Authorized ${ip}` };
    }

    if (action === "revoke") {
      await ec2.send(new RevokeSecurityGroupIngressCommand(params));
      return { ok: true, message: `Revoked ${ip}` };
    }

    return { ok: false, error: "Invalid action" };
  } catch (err) {
    return { ok: false, error: err.message };
  }
};