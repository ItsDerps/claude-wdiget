import json, glob, re, sys, wordfreq
import os
# Folder of the unpacked `npm pack wordlist-english` tarball
P=os.environ.get('WORDLIST_DIR', 'package/')
valid={}
for f in glob.glob(P+'*-words-*.json'):
    if int(re.search(r'-(\d+)\.json',f).group(1))>70: continue
    for w in json.load(open(f)):
        lw=w.lower()
        if lw not in valid or w==lw: valid[lw]=w
block=set("""nigger niggers nigga niggas faggot faggots fag fags retard retards retarded tranny trannies kike kikes spic spics
chink chinks coon coons wetback dyke dykes paki pakis gook gooks alot cum ya'll""".split())
lower_ok=set("""lol haha hahaha lmao wtf idk tbh btw omg bro fuckin aw aka etc app vs pre tech http https com min vol
km cm kg lbs mph bio cafe info selfie emoji wifi blog vlog podcast meme memes ya yep nope gonna wanna gotta kinda sorta
dunno lemme gimme yeah yea okay ok hmm uh um ugh yay woah wow hey hiya cya ttyl brb imo fyi smh asap pls plz thx ty np
sus yall cuz bday tho nah bruh lowkey highkey vibe vibes rizz bestie omw ngl tbf irl dm dms gf bf bae fam lit
salty savage cringe yolo fomo ootd selfies emojis wifi ikr rn atm hbu wbu wyd hmu jk nvm ofc pov""".split())
special={'uk':'UK','usa':'USA','us':'US','tv':'TV','eu':'EU','un':'UN','bbc':'BBC','nfl':'NFL','nba':'NBA','fbi':'FBI','cia':'CIA',
'ceo':'CEO','dna':'DNA','pc':'PC','dc':'DC','ny':'NY','nyc':'NYC','lgbt':'LGBT','gps':'GPS','cnn':'CNN','hiv':'HIV','dvd':'DVD',
'hd':'HD','dj':'DJ','gop':'GOP','nasa':'NASA','fifa':'FIFA','abc':'ABC','cbs':'CBS','mp':'MP','mps':'MPs','phd':'PhD','iphone':'iPhone',
'ipad':'iPad','ios':'iOS','youtube':'YouTube','ebay':'eBay','pm':'pm','am':'am','ai':'AI','ok':'OK','id':'ID','pdf':'PDF','usb':'USB',
'faq':'FAQ','diy':'DIY','vip':'VIP','atm':'ATM','nhs':'NHS','gp':'GP','mr':'Mr','mrs':'Mrs','ms':'Ms','dr':'Dr','tiktok':'TikTok',
'whatsapp':'WhatsApp','linkedin':'LinkedIn','paypal':'PayPal','playstation':'PlayStation','ps':'PS','fc':'FC','url':'URL','api':'API'}
top=wordfreq.top_n_list('en',90000)
topset=set(top)
out=[];seen=set()
for w in top:
    if not re.fullmatch(r"[a-z]+(?:'[a-z]+)?", w) or w in block: continue
    z=wordfreq.zipf_frequency(w,'en')
    if "'" in w:
        if z<3.0: continue
        base,suf=w.split("'")
        ok_base = base in valid or base in ('i','y') or (suf == 't' and base.endswith('n') and base[:-1] in valid)
        if suf not in ('s','t','re','ve','ll','d','m') or not ok_base: continue
        form = 'I'+w[1:] if base=='i' else w
    elif w in special: form=special[w]
    elif w in valid: form=valid[w]
    else:
        if (len(w)<=2 and w not in lower_ok) or z<(2.8 if w in lower_ok else 3.6): continue
        # "dont" -> "don't": skip apostrophe-less contractions
        if any(w[:i]+"'"+w[i:] in topset for i in range(1,len(w))): continue
        form = w if w in lower_ok else w[0].upper()+w[1:]
    if w=='i': form='I'
    if len(w)==1 and w not in ('a','i'): continue
    if form in seen: continue
    seen.add(form); out.append(f"{form}\t{int(z*100)}")
    if len(out)>=50000: break
open(sys.argv[1],'w').write("\n".join(out)+"\n")
print(len(out))
