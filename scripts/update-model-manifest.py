"""Refresh the pinned public model manifest from Hugging Face LFS metadata."""
import json, re, urllib.request
from pathlib import Path
root = Path(__file__).resolve().parents[1]
source = (root/'app/src/main/java/com/coderabyss/mobile/OfflineModels.kt').read_text(encoding='utf-8')
urls = re.findall(r'https://huggingface.co/([^"?]+)\?download=true', source)
entries = [(u.split('/resolve/')[0], u.split('/resolve/main/')[1]) for u in urls]
entries += [('samuelchristlie/Wan2.1-T2V-1.3B-GGUF','Wan2.1-T2V-1.3B-Q4_K_M.gguf'),('city96/umt5-xxl-encoder-gguf','umt5-xxl-encoder-Q4_K_M.gguf'),('Comfy-Org/Wan_2.1_ComfyUI_repackaged','split_files/vae/wan_2.1_vae.safetensors')]
result = {}
for repo, path in dict.fromkeys(entries):
    with urllib.request.urlopen('https://huggingface.co/api/models/'+repo+'?blobs=true') as r:
        info = json.load(r)
    item = next(s for s in info['siblings'] if s['rfilename']==path)
    lfs = item['lfs']
    result[Path(path).name] = {'size':lfs['size'], 'sha256':lfs['sha256'], 'url':f"https://huggingface.co/{repo}/resolve/{info['sha']}/{path}?download=true"}
    print(Path(path).name, lfs['size'])
out = root/'app/src/main/assets/model-manifest.json'
out.parent.mkdir(parents=True,exist_ok=True)
out.write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
