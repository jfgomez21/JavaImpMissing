command! -nargs=? JIM              call <SID>JavaImpInsertMissing()

let s:pluginHome = expand("<sfile>:p:h:h")
let s:loadScript = 1

if !exists("g:JimJavaOpts")
    let g:JimJavaOpts = ""
endif

function! <SID>JavaImpInsertMissing() 
	if has('python3')
		if s:loadScript
			execute "py3file " . substitute(s:pluginHome, "\\", "/", "g") . "/pythonx/jim.py"

			let s:loadScript = 0
		endif

		execute "python3 jim_import_missing()"
	else
		echom 'JavaImpMissing: No python support'
	endif
endfunction
