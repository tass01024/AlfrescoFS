AlfrescoFS
==========

Alfresco FileSystem using JNA &amp; Dynamic Extensions

Related to Alfresco filesystem created using C code and Webscript conneciton (alffs) - https://github.com/LotharMaerkle/alffs

Using Alfresco Dynamic Extensions - https://github.com/laurentvdl/dynamic-extensions-for-alfresco


Assumptions:

- OS username is equivalent to Alfresco Username, a UID mapping is done to OS username, which is used for ALfresco transactions. If username does not exists on Alfresco, Alfresco creates a quest like user context, where you can read public folders (Everyone has access)
