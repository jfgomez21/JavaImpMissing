command! -nargs=? JIM              call <SID>JavaImpInsertMissing()

let s:pluginHome = expand("<sfile>:p:h:h")

function! <SID>JavaImpInsertMissing() 
	if has('python3')
		execute "py3file " . s:pluginHome . "/pythonx/jim.py"
	else
		echom 'JavaImpImportMissing: No python support'
	endif
endfunction
