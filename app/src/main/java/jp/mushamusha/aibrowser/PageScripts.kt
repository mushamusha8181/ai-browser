package jp.mushamusha.aibrowser

object PageScripts {
    val snapshot = """
        (function(){
          function clean(s,n){ return (s||'').replace(/\s+/g,' ').trim().slice(0,n); }
          const title = document.title || '';
          const url = location.href;
          const lang = document.documentElement.lang || '';
          const text = clean((document.body && document.body.innerText) || '', 120000);
          let linkBudget=80000;
          const links=[];
          for(const a of Array.from(document.querySelectorAll('a[href]')).slice(0,300)){
            if(linkBudget<=0) break;
            const text=clean(a.innerText,300);
            const href=clean(a.href||'',Math.min(2000,linkBudget));
            linkBudget-=text.length+href.length;
            links.push({text,href});
          }
          const headings = Array.from(document.querySelectorAll('h1,h2,h3')).slice(0,200).map(h => ({
            level: h.tagName, text: clean(h.innerText,500)
          }));
          let tableBudget=80000;
          const tables=[];
          for(const t of Array.from(document.querySelectorAll('table')).slice(0,12)){
            const rows=[];
            for(const r of Array.from(t.rows).slice(0,120)){
              const cells=[];
              for(const c of Array.from(r.cells).slice(0,30)){
                if(tableBudget<=0) break;
                const raw=clean(c.innerText,1200);
                const value=raw.slice(0,Math.min(raw.length,tableBudget));
                tableBudget-=value.length;
                cells.push(value);
              }
              if(cells.length) rows.push(cells);
              if(tableBudget<=0) break;
            }
            if(rows.length) tables.push(rows);
            if(tableBudget<=0) break;
          }
          return JSON.stringify({title,url,lang,text,headings,links,tables});
        })();
    """.trimIndent()

    val selectedText = """
        (function(){ return (window.getSelection ? window.getSelection().toString() : '').slice(0,50000); })();
    """.trimIndent()

    val readerSnapshot = """
        (function(){
          function clean(s,n){ return (s||'').replace(/\s+/g,' ').trim().slice(0,n); }
          const candidates=Array.from(document.querySelectorAll('article,main,[role=main]'));
          let root=candidates.sort((a,b)=>(b.innerText||'').length-(a.innerText||'').length)[0] || document.body;
          let remaining=180000;
          const blocks=[];
          for(const e of Array.from(root.querySelectorAll('h1,h2,h3,p,li,blockquote,pre')).slice(0,800)){
            if(remaining<=0) break;
            const raw=clean(e.innerText||e.textContent,6000);
            if(raw.length<2) continue;
            const text=raw.slice(0,Math.min(raw.length,remaining));
            remaining-=text.length;
            blocks.push({tag:e.tagName.toLowerCase(),text});
          }
          if(!blocks.length){
            const fallback=clean((root&&root.innerText)||'',120000);
            if(fallback) blocks.push({tag:'p',text:fallback});
          }
          return JSON.stringify({title:document.title||'',url:location.href,blocks:blocks});
        })();
    """.trimIndent()

    val interactiveElements = """
        (function(){
          function clean(s,n){ return (s||'').replace(/\s+/g,' ').trim().slice(0,n); }
          function esc(s){ return (window.CSS && CSS.escape) ? CSS.escape(s) : String(s).replace(/[^a-zA-Z0-9_-]/g,'\\$&'); }
          function path(el){
            if(!el || el.nodeType!==1) return '';
            if(el.id) return '#'+esc(el.id);
            const parts=[];
            while(el && el.nodeType===1 && el!==document.body){
              let p=el.tagName.toLowerCase();
              const cls=Array.from(el.classList||[]).filter(Boolean).slice(0,2);
              if(cls.length) p += '.'+cls.map(esc).join('.');
              const parent=el.parentElement;
              if(parent){
                const same=Array.from(parent.children).filter(x=>x.tagName===el.tagName);
                if(same.length>1) p += ':nth-of-type('+(same.indexOf(el)+1)+')';
              }
              parts.unshift(p); el=parent;
              if(parts.length>=6) break;
            }
            return parts.join(' > ');
          }
          const q='a[href],button,input:not([type=password]),select,textarea,[role=button],[onclick]';
          return JSON.stringify(Array.from(document.querySelectorAll(q)).filter(e=>{
            const r=e.getBoundingClientRect(); return r.width>0 && r.height>0;
          }).slice(0,400).map((e,i)=>({
            i, tag:e.tagName.toLowerCase(), selector:path(e), text:clean(e.innerText||e.getAttribute('aria-label')||e.getAttribute('placeholder'),300),
            type:e.getAttribute('type')||'', name:e.getAttribute('name')||'', autocomplete:e.getAttribute('autocomplete')||'',
            href:clean(e.href||'',2000), placeholder:clean(e.getAttribute('placeholder')||'',500),
            title:clean(e.getAttribute('title')||'',300),
            buttonValue:(/^(submit|button|reset)$/i.test(e.getAttribute('type')||'')?clean(e.value||'',300):'')
          })));
        })();
    """.trimIndent()

    val installInspector = """
        (function(){
          if(window.__mushaInspectHandler){ document.removeEventListener('click',window.__mushaInspectHandler,true); }
          window.__mushaInspectHandler=null;
          window.__mushaInspectResult='';
          function clean(s,n){ return (s||'').replace(/\s+/g,' ').trim().slice(0,n); }
          function esc(s){ return (window.CSS && CSS.escape) ? CSS.escape(s) : String(s).replace(/[^a-zA-Z0-9_-]/g,'\\$&'); }
          function path(el){
            if(el.id) return '#'+esc(el.id);
            const parts=[];
            while(el && el.nodeType===1 && el!==document.body){
              let p=el.tagName.toLowerCase();
              const cls=Array.from(el.classList||[]).filter(Boolean).slice(0,3);
              if(cls.length) p += '.'+cls.map(esc).join('.');
              const parent=el.parentElement;
              if(parent){
                const same=Array.from(parent.children).filter(x=>x.tagName===el.tagName);
                if(same.length>1) p += ':nth-of-type('+(same.indexOf(el)+1)+')';
              }
              parts.unshift(p); el=parent;
              if(parts.length>=8) break;
            }
            return parts.join(' > ');
          }
          const handler=function(e){
            e.preventDefault(); e.stopPropagation(); e.stopImmediatePropagation();
            const el=e.target;
            const old=el.style.outline; el.style.outline='3px solid #ff2d55';
            const secretName=/token|secret|credential|authorization|csrf|xsrf|nonce|session|api[-_]?key/i;
            const attrs={}; Array.from(el.attributes||[]).forEach(a=>{
              if(/^value$/i.test(a.name) || /^on/i.test(a.name)) return;
              attrs[a.name]=secretName.test(a.name) ? '[MASKED]' : clean(a.value,1000);
            });
            const clone=el.cloneNode(true);
            const nodes=[clone].concat(Array.from(clone.querySelectorAll ? clone.querySelectorAll('*') : []));
            nodes.forEach(n=>{
              if(!n.removeAttribute) return;
              n.removeAttribute('value');
              Array.from(n.attributes||[]).forEach(a=>{
                if(/^on/i.test(a.name)) n.removeAttribute(a.name);
                else if(secretName.test(a.name)) n.setAttribute(a.name,'[MASKED]');
              });
              if((n.tagName||'').toLowerCase()==='textarea') n.textContent='';
              if((n.getAttribute&&n.getAttribute('type')||'').toLowerCase()==='password') n.setAttribute('value','[MASKED]');
            });
            window.__mushaInspectResult=JSON.stringify({
              tag:el.tagName.toLowerCase(), id:el.id||'', classes:Array.from(el.classList||[]),
              selector:path(el), text:clean(el.innerText||el.textContent,4000), href:clean(el.href||'',2000), src:clean(el.src||'',2000),
              attributes:attrs, html:clean(clone.outerHTML||'',12000)
            });
            setTimeout(()=>{el.style.outline=old;},1200);
            document.removeEventListener('click',handler,true);
            window.__mushaInspectHandler=null;
          };
          window.__mushaInspectHandler=handler;
          document.addEventListener('click',handler,true);
          return 'READY';
        })();
    """.trimIndent()

    val cancelInspector = """
        (function(){
          if(window.__mushaInspectHandler){ document.removeEventListener('click',window.__mushaInspectHandler,true); }
          window.__mushaInspectHandler=null;
          window.__mushaInspectResult='';
          return 'CANCELLED';
        })();
    """.trimIndent()

    val mediaSnapshot = """
        (function(){
          function clean(s,n){ return (s||'').replace(/\s+/g,' ').trim().slice(0,n); }
          const videos=Array.from(document.querySelectorAll('video')).slice(0,30).map((v,i)=>({
            index:i, src:clean(v.currentSrc||v.src||'',4000), poster:clean(v.poster||'',4000), duration:isFinite(v.duration)?v.duration:null,
            tracks:Array.from(v.querySelectorAll('track')).slice(0,20).map(t=>({kind:t.kind,label:clean(t.label,300),srclang:t.srclang,src:clean(t.src||'',4000)}))
          }));
          const audios=Array.from(document.querySelectorAll('audio')).slice(0,30).map((a,i)=>({
            index:i, src:clean(a.currentSrc||a.src||'',4000), duration:isFinite(a.duration)?a.duration:null
          }));
          const images=Array.from(document.images).filter(img=>{
            const r=img.getBoundingClientRect(); return r.width>=120 && r.height>=80;
          }).slice(0,80).map((img,i)=>({
            index:i, src:clean(img.currentSrc||img.src||'',4000), alt:clean(img.alt,500), title:clean(img.title,300),
            width:img.naturalWidth||0,height:img.naturalHeight||0
          }));
          let transcriptBudget=60000;
          const transcriptHints=[];
          for(const e of Array.from(document.querySelectorAll('[class*=transcript],[id*=transcript],[class*=caption],[id*=caption]')).slice(0,20)){
            if(transcriptBudget<=0) break;
            const text=clean(e.innerText||e.textContent,Math.min(8000,transcriptBudget));
            if(text){ transcriptHints.push(text); transcriptBudget-=text.length; }
          }
          return JSON.stringify({title:document.title,url:location.href,videos,audios,images,transcriptHints});
        })();
    """.trimIndent()

    val securitySnapshot = """
        (function(){
          function clean(s,n){ return (s||'').replace(/\s+/g,' ').trim().slice(0,n); }
          const forms=Array.from(document.forms).slice(0,50).map(f=>({
            method:(f.method||'get').toLowerCase(), action:clean(f.action||'',4000),
            passwordFields:f.querySelectorAll('input[type=password]').length,
            fileFields:f.querySelectorAll('input[type=file]').length,
            submitLabels:Array.from(f.querySelectorAll('button,input[type=submit]')).slice(0,20).map(e=>clean(e.innerText||e.value||e.getAttribute('aria-label'),200))
          }));
          const downloads=Array.from(document.querySelectorAll('a[download],a[href]')).filter(a=>{
            const h=(a.href||'').toLowerCase(); return a.hasAttribute('download') || /\.(apk|exe|msi|dmg|zip|rar|7z|pdf)(\?|#|$)/.test(h);
          }).slice(0,80).map(a=>({text:clean(a.innerText,300),href:clean(a.href||'',4000),download:clean(a.getAttribute('download')||'',300)}));
          return JSON.stringify({
            title:document.title,url:location.href,origin:location.origin,protocol:location.protocol,
            passwordInputs:document.querySelectorAll('input[type=password]').length,
            iframes:document.querySelectorAll('iframe').length,
            forms,downloads,
            textSample:clean((document.body&&document.body.innerText)||'',12000)
          });
        })();
    """.trimIndent()

    fun scrapeSelector(selectorJson: String): String = """
        (function(){
          const sel=$selectorJson;
          function clean(s,n){ return (s||'').replace(/\s+/g,' ').trim().slice(0,n); }
          try {
            let budget=180000;
            const rows=[];
            const nodes=Array.from(document.querySelectorAll(sel)).slice(0,500);
            for(let i=0;i<nodes.length && budget>0;i++){
              const e=nodes[i];
              const text=clean(e.innerText||e.textContent,Math.min(3000,budget));
              const href=clean(e.href||'',Math.min(1200,Math.max(0,budget-text.length)));
              const src=clean(e.src||'',Math.min(1200,Math.max(0,budget-text.length-href.length)));
              const alt=clean(e.getAttribute('alt')||'',Math.min(300,Math.max(0,budget-text.length-href.length-src.length)));
              const title=clean(e.getAttribute('title')||'',Math.min(300,Math.max(0,budget-text.length-href.length-src.length-alt.length)));
              budget-=text.length+href.length+src.length+alt.length+title.length+80;
              rows.push({index:i,tag:e.tagName.toLowerCase(),text,href,src,alt,title});
            }
            return JSON.stringify({selector:sel,count:rows.length,items:rows});
          } catch(err) { return JSON.stringify({error:String(err)}); }
        })();
    """.trimIndent()

    fun agentAction(action: String, selectorJson: String?, textJson: String?, urlJson: String?): String {
        return when (action) {
            "click" -> """(function(){try{const e=document.querySelector(${selectorJson ?: "''"}); if(!e)return 'NOT_FOUND'; e.scrollIntoView({block:'center'}); e.click(); return 'OK';}catch(x){return 'ERROR:'+x;}})();"""
            "fill" -> """(function(){try{const e=document.querySelector(${selectorJson ?: "''"}); if(!e)return 'NOT_FOUND'; const meta=[e.type,e.name,e.autocomplete,e.placeholder,e.getAttribute&&e.getAttribute('aria-label')].join(' ').toLowerCase(); if(/password|one-time-code|otp|2fa|verification|cc-number|cc-csc|cc-exp|credit.?card|cvv|bank|account.?number/.test(meta))return 'SENSITIVE_BLOCKED'; const v=${textJson ?: "''"}; e.focus(); if(e.isContentEditable){e.textContent=v;} else if(e.tagName==='SELECT'){e.value=v;} else {const proto=e.tagName==='TEXTAREA'?HTMLTextAreaElement.prototype:HTMLInputElement.prototype; const d=Object.getOwnPropertyDescriptor(proto,'value'); if(d&&d.set)d.set.call(e,v); else e.value=v;} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return 'OK';}catch(x){return 'ERROR:'+x;}})();"""
            "navigate" -> """(function(){location.href=${urlJson ?: "''"}; return 'OK';})();"""
            "scroll" -> """(function(){window.scrollBy({top:Math.round(innerHeight*0.8),behavior:'smooth'});return 'OK';})();"""
            else -> "'UNSUPPORTED'"
        }
    }
}
