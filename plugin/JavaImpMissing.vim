command! -nargs=? JIM              call <SID>JavaImpInsertMissing()

let s:pluginHome = expand("<sfile>:p:h:h")

function! <SID>JavaImpInsertMissing() 
	if has('python3')
		let l:path = substitute(s:pluginHome, "\\", "/", "g")

		execute "python3 sys.argv = ['" . l:path . "/pythonx/jim.py']" 
		execute "py3file " . l:path . "/pythonx/jim.py"
	else
		echom 'JavaImpMissing: No python support'
	endif
endfunction
