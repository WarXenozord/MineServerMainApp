// minecraft/build-paper.js
import fs from 'fs';
import https from 'https';

const VERSION = '1.21.8'; // target MC version
const PAPER_API = `https://api.papermc.io/v2/projects/paper/versions/${VERSION}`;

async function getLatestBuild() {
  return new Promise((resolve, reject) => {
    https.get(PAPER_API, res => {
      let data = '';
      res.on('data', c => (data += c));
      res.on('end', () => {
        const json = JSON.parse(data);
        const latest = json.builds.at(-1);
        resolve(latest);
      });
    }).on('error', reject);
  });
}

async function downloadPaper(build) {
  const url = `https://api.papermc.io/v2/projects/paper/versions/${VERSION}/builds/${build}/downloads/paper-${VERSION}-${build}.jar`;
  const dest = './minecraft/server/paper.jar';
  fs.mkdirSync('./minecraft/server', { recursive: true });
  
  console.log(`Downloading Paper build ${build}...`);
  return new Promise((resolve, reject) => {
    const file = fs.createWriteStream(dest);
    https.get(url, res => res.pipe(file));
    file.on('finish', () => {
      file.close();
      console.log('Paper downloaded.');
      resolve();
    });
    file.on('error', reject);
  });
}

(async () => {
  const build = await getLatestBuild();
  await downloadPaper(build);
})();